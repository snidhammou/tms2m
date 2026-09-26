package com.tms.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tms.server.domain.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fonctionnalités "phase 1" inspirées de TOMS : organisations, zéro contact, planification, résultats. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tmsphase1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "tms.storage.apk-dir=build/test-apks-p1",
        // "./" volontaire : reproduit la configuration par défaut (./data/artifacts)
        "tms.storage.artifact-dir=./build/test-artifacts-p1",
        "tms.enrollment.key=TEST-KEY"
})
@AutoConfigureMockMvc
class Phase1IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @Test
    void organizationHierarchyTargetsDescendants() throws Exception {
        long root = json(admin(post("/api/admin/v1/organizations"), "{\"name\":\"Acquéreur\"}"), 201).get("id").asLong();
        long region = json(admin(post("/api/admin/v1/organizations"),
                "{\"name\":\"Région Nord\",\"parentId\":" + root + "}"), 201).get("id").asLong();
        long merchant = json(admin(post("/api/admin/v1/merchants"),
                "{\"code\":\"M-ORG\",\"name\":\"Boutique Nord\",\"organizationId\":" + region + "}"), 201).get("id").asLong();

        long terminalId = enroll("ORG-0001").get("terminalId").asLong();
        admin(put("/api/admin/v1/terminals/" + terminalId), "{\"merchantId\":" + merchant + "}").andExpect(status().isOk());
        enroll("ORG-0002"); // hors organisation

        // Cibler la racine inclut la sous-organisation
        JsonNode dep = json(admin(post("/api/admin/v1/deployments"),
                "{\"type\":\"DIAGNOSE\",\"target\":{\"organizationId\":" + root + "}}"), 201);
        assertThat(dep.get("taskCount").asInt()).isEqualTo(1);

        JsonNode list = json(adminGet("/api/admin/v1/terminals?organizationId=" + root), 200);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("organizationName").asText()).isEqualTo("Région Nord");

        // Une organisation non vide ne peut pas être supprimée
        adminDo(delete("/api/admin/v1/organizations/" + root)).andExpect(status().isConflict());
    }

    @Test
    void zeroTouchTemplateProvisionsPreRegisteredTerminal() throws Exception {
        long appId = json(mvc.perform(multipart("/api/admin/v1/apps")
                .file(new MockMultipartFile("file", "pay.apk", "application/octet-stream", "fake".getBytes()))
                .param("packageName", "com.acme.pay").param("versionCode", "3")
                .with(httpBasic("admin", "admin123"))), 201).get("id").asLong();
        long paramTpl = json(admin(post("/api/admin/v1/parameter-templates"),
                "{\"name\":\"Pay prod\",\"packageName\":\"com.acme.pay\",\"values\":{\"host\":\"10.1.1.1\",\"port\":\"443\"}}"),
                201).get("id").asLong();
        long depTpl = json(admin(post("/api/admin/v1/deployment-templates"),
                "{\"name\":\"Standard caisse\",\"appIds\":[" + appId + "],\"parameterTemplateIds\":[" + paramTpl + "],"
                        + "\"autoRunPackage\":\"com.acme.pay\",\"kioskPackages\":[\"com.acme.pay\"]}"), 201).get("id").asLong();
        long group = json(admin(post("/api/admin/v1/groups"),
                "{\"name\":\"Caisses ZT\",\"templateId\":" + depTpl + "}"), 201).get("id").asLong();

        // Pré-enregistrement par lots (doublon ignoré)
        JsonNode bulk = json(admin(post("/api/admin/v1/terminals/bulk"),
                "{\"serialNumbers\":\"ZT-001\\nZT-002\\nZT-001\",\"manufacturer\":\"SUNMI\",\"groupId\":" + group + "}"), 201);
        assertThat(bulk.get("created").asInt()).isEqualTo(2);

        // À l'enrôlement, le terminal reçoit tout le modèle
        JsonNode enrolled = enroll("ZT-001");
        JsonNode tasks = json(device(post("/api/device/v1/heartbeat"), enrolled.get("deviceToken").asText())
                .contentType(MediaType.APPLICATION_JSON).content("{}"), 200).get("tasks");
        assertThat(tasks).extracting(t -> t.get("type").asText())
                .containsExactlyInAnyOrder("INSTALL_APP", "PUSH_PARAMS", "SET_AUTORUN", "SET_KIOSK");

        // Les paramètres du modèle sont résolus au niveau du groupe
        JsonNode params = json(device(get("/api/device/v1/parameters?packageName=com.acme.pay"),
                enrolled.get("deviceToken").asText()), 200).get("parameters");
        assertThat(params.get("host").asText()).isEqualTo("10.1.1.1");

        JsonNode history = json(adminGet("/api/admin/v1/terminals/" + enrolled.get("terminalId").asLong() + "/history"), 200);
        assertThat(history).extracting(e -> e.get("action").asText()).contains("ZERO_TOUCH", "ENROLLED");
    }

    @Test
    void scheduledTaskIsNotDeliveredBeforeItsTime() throws Exception {
        JsonNode e = enroll("SCHED-001");
        String token = e.get("deviceToken").asText();
        String future = Instant.now().plusSeconds(3600).toString();
        admin(post("/api/admin/v1/deployments"), "{\"type\":\"REBOOT\",\"target\":{\"terminalIds\":["
                + e.get("terminalId").asLong() + "]},\"schedule\":{\"notBefore\":\"" + future + "\"}}")
                .andExpect(status().isCreated());
        JsonNode hb = json(device(post("/api/device/v1/heartbeat"), token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"), 200);
        assertThat(hb.get("tasks")).isEmpty();

        // Fenêtre invalide refusée
        admin(post("/api/admin/v1/deployments"), "{\"type\":\"REBOOT\",\"target\":{\"terminalIds\":["
                + e.get("terminalId").asLong() + "]},\"schedule\":{\"windowStart\":\"22:00\"}}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void maintenanceWindowIncludingOvernight() {
        Task day = new Task();
        day.setWindowStart(LocalTime.of(9, 0));
        day.setWindowEnd(LocalTime.of(17, 0));
        assertThat(day.isDeliverableAt(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC)).isTrue();
        assertThat(day.isDeliverableAt(Instant.parse("2026-01-01T18:00:00Z"), ZoneOffset.UTC)).isFalse();

        Task night = new Task();
        night.setWindowStart(LocalTime.of(22, 0));
        night.setWindowEnd(LocalTime.of(6, 0));
        assertThat(night.isDeliverableAt(Instant.parse("2026-01-01T23:30:00Z"), ZoneOffset.UTC)).isTrue();
        assertThat(night.isDeliverableAt(Instant.parse("2026-01-01T03:00:00Z"), ZoneOffset.UTC)).isTrue();
        assertThat(night.isDeliverableAt(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC)).isFalse();
    }

    @Test
    void diagnosticResultLogsArtifactMetricsAndInventoryHistory() throws Exception {
        JsonNode e = enroll("DIAG-001");
        String token = e.get("deviceToken").asText();
        long terminalId = e.get("terminalId").asLong();

        // Supervision + premier inventaire
        mvc.perform(device(post("/api/device/v1/heartbeat"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"batteryLevel\":15,\"storageTotalBytes\":1000,\"storageFreeBytes\":50,\"ramAvailBytes\":300,"
                        + "\"networkType\":\"WIFI\",\"rxBytes\":1000,\"txBytes\":500,\"deviceOwner\":true,"
                        + "\"installedApps\":[{\"packageName\":\"a.b\",\"versionName\":\"1\",\"versionCode\":1}]}"))
                .andExpect(status().isOk());
        mvc.perform(device(post("/api/device/v1/heartbeat"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"installedApps\":[{\"packageName\":\"a.b\",\"versionName\":\"2\",\"versionCode\":2},"
                        + "{\"packageName\":\"c.d\",\"versionName\":\"1\",\"versionCode\":1}]}")).andExpect(status().isOk());

        assertThat(json(adminGet("/api/admin/v1/terminals/" + terminalId + "/metrics?hours=1"), 200)).hasSize(2);
        JsonNode history = json(adminGet("/api/admin/v1/terminals/" + terminalId + "/history"), 200);
        assertThat(history).extracting(x -> x.get("action").asText()).contains("APP_INSTALLED", "APP_UPDATED");
        JsonNode dash = json(adminGet("/api/admin/v1/dashboard"), 200);
        assertThat(dash.get("lowBattery").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(dash.get("lowStorage").asLong()).isGreaterThanOrEqualTo(1);

        // Diagnostic : rapport structuré
        admin(post("/api/admin/v1/deployments"), "{\"type\":\"DIAGNOSE\",\"target\":{\"terminalIds\":[" + terminalId + "]}}");
        long diagTask = json(device(post("/api/device/v1/heartbeat"), token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"), 200).get("tasks").get(0).get("id").asLong();
        mvc.perform(device(post("/api/device/v1/tasks/" + diagTask + "/status"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUCCESS\",\"message\":\"ok\",\"result\":{\"serverReachable\":true,\"storageFreePercent\":5}}"))
                .andExpect(status().isNoContent());
        JsonNode diag = json(adminGet("/api/admin/v1/tasks?terminalId=" + terminalId), 200).get(0);
        assertThat(diag.get("result").get("serverReachable").asBoolean()).isTrue();

        // Extraction de logs : fichier téléversé puis téléchargé par l'admin
        admin(post("/api/admin/v1/deployments"), "{\"type\":\"EXTRACT_LOGS\",\"logLines\":500,\"target\":{\"terminalIds\":[" + terminalId + "]}}");
        long logTask = json(device(post("/api/device/v1/heartbeat"), token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"), 200).get("tasks").get(0).get("id").asLong();
        mvc.perform(multipart("/api/device/v1/tasks/" + logTask + "/artifact")
                        .file(new MockMultipartFile("file", "agent-logs.txt", "text/plain", "ligne1\nligne2".getBytes()))
                        .header("X-Device-Token", token))
                .andExpect(status().isNoContent());
        byte[] content = adminDo(get("/api/admin/v1/tasks/" + logTask + "/artifact"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(content)).isEqualTo("ligne1\nligne2");

        // Logs par application : 20 000 lignes par défaut, fenêtre "depuis N minutes" plafonnée à 24 h
        JsonNode perApp = json(admin(post("/api/admin/v1/deployments"), "{\"type\":\"EXTRACT_LOGS\",\"packageName\":\"com.acme.pay\","
                + "\"logSinceMinutes\":5000,\"target\":{\"terminalIds\":[" + terminalId + "]}}"), 201);
        JsonNode perAppTask = json(adminGet("/api/admin/v1/tasks?deploymentId=" + perApp.get("deploymentId").asText()), 200).get(0);
        assertThat(perAppTask.get("payload").get("packageName").asText()).isEqualTo("com.acme.pay");
        assertThat(perAppTask.get("payload").get("lines").asInt()).isEqualTo(20000);
        assertThat(perAppTask.get("payload").get("sinceMinutes").asInt()).isEqualTo(1440);

        // Plage "du … au …" et plus de 20 000 lignes
        JsonNode range = json(admin(post("/api/admin/v1/deployments"), "{\"type\":\"EXTRACT_LOGS\",\"logLines\":150000,"
                + "\"logFrom\":\"2026-09-26T20:00:00Z\",\"logTo\":\"2026-09-26T21:00:00Z\",\"target\":{\"terminalIds\":[" + terminalId + "]}}"), 201);
        JsonNode rangeTask = json(adminGet("/api/admin/v1/tasks?deploymentId=" + range.get("deploymentId").asText()), 200).get(0);
        assertThat(rangeTask.get("payload").get("lines").asInt()).isEqualTo(150000);
        assertThat(rangeTask.get("payload").get("fromEpochMs").asLong()).isEqualTo(Instant.parse("2026-09-26T20:00:00Z").toEpochMilli());
        assertThat(rangeTask.get("payload").get("toEpochMs").asLong()).isEqualTo(Instant.parse("2026-09-26T21:00:00Z").toEpochMilli());
        admin(post("/api/admin/v1/deployments"), "{\"type\":\"EXTRACT_LOGS\",\"logFrom\":\"2026-09-26T21:00:00Z\","
                + "\"logTo\":\"2026-09-26T20:00:00Z\",\"target\":{\"terminalIds\":[" + terminalId + "]}}")
                .andExpect(status().isBadRequest());

        // Extraction de fichier : chemin relatif refusé
        admin(post("/api/admin/v1/deployments"), "{\"type\":\"EXTRACT_FILE\",\"filePath\":\"sdcard/x\",\"target\":{\"terminalIds\":[" + terminalId + "]}}")
                .andExpect(status().isBadRequest());
    }

    // ---- helpers ----

    @Test
    void forcedSyncWakesLongPollAndIconsAreCollected() throws Exception {
        Files.deleteIfExists(Path.of("build/test-apks-p1/icons/com.acme.icon.png")); // runs précédents
        JsonNode e = enroll("SYNC-001");
        String token = e.get("deviceToken").asText();
        long id = e.get("terminalId").asLong();

        // Canal temps réel ouvert par l'agent
        MvcResult waiting = mvc.perform(device(get("/api/device/v1/wait?timeout=30"), token))
                .andExpect(request().asyncStarted()).andReturn();
        assertThat(json(adminGet("/api/admin/v1/terminals/" + id), 200).get("realtime").asBoolean()).isTrue();

        JsonNode sync = json(admin(post("/api/admin/v1/terminals/" + id + "/sync"), "{}"), 200);
        assertThat(sync.get("delivered").asBoolean()).isTrue();
        JsonNode woke = om.readTree(mvc.perform(asyncDispatch(waiting)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(woke.get("sync").asBoolean()).isTrue();

        // Terminal hors canal : le signal est conservé pour sa prochaine attente
        assertThat(json(admin(post("/api/admin/v1/terminals/" + id + "/sync"), "{}"), 200)
                .get("delivered").asBoolean()).isFalse();

        // Icônes : le serveur réclame celles qu'il n'a pas, l'agent les envoie
        JsonNode hb = json(device(post("/api/device/v1/heartbeat"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"installedApps\":[{\"packageName\":\"com.acme.icon\",\"versionName\":\"1\",\"versionCode\":1}]}"), 200);
        assertThat(hb.get("iconsWanted")).extracting(JsonNode::asText).containsExactly("com.acme.icon");
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0};
        mvc.perform(device(post("/api/device/v1/icons/com.acme.icon"), token)
                .contentType(MediaType.IMAGE_PNG).content(png)).andExpect(status().isNoContent());
        mvc.perform(device(post("/api/device/v1/icons/com.acme.bad"), token)
                .contentType(MediaType.IMAGE_PNG).content("pas un png".getBytes())).andExpect(status().isBadRequest());
        adminGet("/api/admin/v1/apps/icons/com.acme.icon").andExpect(status().isOk());
        adminGet("/api/admin/v1/apps/icons/com.acme.unknown").andExpect(status().isNotFound());
    }

    @Test
    void agentAutoUpdateTargetsSameVariantOnce() throws Exception {
        // APK illisible (factice) : les champs saisis servent de manifeste
        for (String[] v : new String[][]{{"9.0.1-pax", "90001"}, {"9.0.2", "90002"}}) {
            mvc.perform(multipart("/api/admin/v1/apps")
                    .file(new MockMultipartFile("file", "agent.apk", "application/octet-stream", v[0].getBytes()))
                    .param("packageName", "com.tms.agent").param("versionName", v[0]).param("versionCode", v[1])
                    .with(httpBasic("admin", "admin123"))).andExpect(status().isCreated());
        }
        String token = enroll("AUTOUPD-001").get("deviceToken").asText();
        String hbBody = "{\"agentVersion\":\"1.0.0-pax\",\"installedApps\":[{\"packageName\":\"com.tms.agent\","
                + "\"versionName\":\"1.0.0-pax\",\"versionCode\":1}]}";

        JsonNode tasks = json(device(post("/api/device/v1/heartbeat"), token)
                .contentType(MediaType.APPLICATION_JSON).content(hbBody), 200).get("tasks");
        // Variante PAX uniquement (la version universal plus récente n'est pas proposée)
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).get("type").asText()).isEqualTo("INSTALL_APP");
        assertThat(tasks.get(0).get("payload").get("versionName").asText()).isEqualTo("9.0.1-pax");

        // Heartbeat suivant : la même tâche est renvoyée, sans doublon
        JsonNode again = json(device(post("/api/device/v1/heartbeat"), token)
                .contentType(MediaType.APPLICATION_JSON).content(hbBody), 200).get("tasks");
        assertThat(again).hasSize(1);
        assertThat(again.get(0).get("id").asLong()).isEqualTo(tasks.get(0).get("id").asLong());
    }

    private JsonNode enroll(String serial) throws Exception {
        return json(mvc.perform(post("/api/device/v1/enroll").contentType(MediaType.APPLICATION_JSON)
                .content("{\"serialNumber\":\"" + serial + "\",\"enrollmentKey\":\"TEST-KEY\",\"manufacturer\":\"SUNMI\"}")), 200);
    }

    private MockHttpServletRequestBuilder device(MockHttpServletRequestBuilder req, String token) {
        return req.header("X-Device-Token", token);
    }

    private ResultActions admin(MockHttpServletRequestBuilder req, String body) throws Exception {
        return mvc.perform(req.with(httpBasic("admin", "admin123")).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions adminGet(String url) throws Exception {
        return adminDo(get(url));
    }

    private ResultActions adminDo(MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.with(httpBasic("admin", "admin123")));
    }

    private JsonNode json(MockHttpServletRequestBuilder req, int expected) throws Exception {
        return json(mvc.perform(req), expected);
    }

    private JsonNode json(ResultActions actions, int expected) throws Exception {
        String body = actions.andExpect(status().is(expected)).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return om.readTree(body);
    }
}
