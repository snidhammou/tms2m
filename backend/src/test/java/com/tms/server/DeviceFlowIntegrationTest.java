package com.tms.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tmstest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "tms.storage.apk-dir=build/test-apks",
        "tms.enrollment.key=TEST-KEY"
})
@AutoConfigureMockMvc
class DeviceFlowIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @Test
    void enrollHeartbeatTaskLifecycleAndParameters() throws Exception {
        JsonNode enrolled = enroll("PAX-0001", "PAX", "A920");
        String token = enrolled.get("deviceToken").asText();
        long terminalId = enrolled.get("terminalId").asLong();

        // Sans jeton : refusé
        mvc.perform(post("/api/device/v1/heartbeat").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        // Groupe + rattachement du terminal
        long groupId = json(admin(post("/api/admin/v1/groups"), "{\"name\":\"Casablanca\"}"), 201).get("id").asLong();
        admin(put("/api/admin/v1/terminals/" + terminalId), "{\"groupId\":" + groupId + ",\"tid\":\"T0001\"}", 200);

        // Paramètres à 3 niveaux : le plus spécifique l'emporte
        String pkg = "com.acme.payment";
        admin(put("/api/admin/v1/parameters"),
                "{\"scope\":\"GLOBAL\",\"packageName\":\"" + pkg + "\",\"values\":{\"host\":\"global\",\"timeout\":\"30\",\"currency\":\"MAD\"}}", 200);
        admin(put("/api/admin/v1/parameters"),
                "{\"scope\":\"GROUP\",\"scopeRef\":" + groupId + ",\"packageName\":\"" + pkg + "\",\"values\":{\"host\":\"group\",\"timeout\":\"45\"}}", 200);
        admin(put("/api/admin/v1/parameters"),
                "{\"scope\":\"TERMINAL\",\"scopeRef\":" + terminalId + ",\"packageName\":\"" + pkg + "\",\"values\":{\"host\":\"terminal\"}}", 200);

        JsonNode params = json(device(get("/api/device/v1/parameters?packageName=" + pkg), token), 200).get("parameters");
        assertThat(params.get("host").asText()).isEqualTo("terminal");
        assertThat(params.get("timeout").asText()).isEqualTo("45");
        assertThat(params.get("currency").asText()).isEqualTo("MAD");

        // Déploiement REBOOT ciblant le groupe
        JsonNode deployment = json(admin(post("/api/admin/v1/deployments"),
                "{\"type\":\"REBOOT\",\"target\":{\"groupId\":" + groupId + "}}"), 201);
        assertThat(deployment.get("taskCount").asInt()).isEqualTo(1);

        // Heartbeat : la tâche est livrée avec l'inventaire
        JsonNode hb = json(device(post("/api/device/v1/heartbeat"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batteryLevel\":87,\"installedApps\":[{\"packageName\":\"" + pkg + "\",\"versionName\":\"1.0\",\"versionCode\":1}]}"), 200);
        JsonNode task = hb.get("tasks").get(0);
        assertThat(task.get("type").asText()).isEqualTo("REBOOT");
        long taskId = task.get("id").asLong();

        mvc.perform(device(post("/api/device/v1/tasks/" + taskId + "/status"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUCCESS\",\"message\":\"rebooted\"}"))
                .andExpect(status().isNoContent());

        JsonNode tasks = json(mvc.perform(get("/api/admin/v1/tasks?terminalId=" + terminalId)
                .with(httpBasic("admin", "admin123"))), 200);
        assertThat(tasks.get(0).get("status").asText()).isEqualTo("SUCCESS");

        // Plus de tâche au heartbeat suivant
        JsonNode hb2 = json(device(post("/api/device/v1/heartbeat"), token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"), 200);
        assertThat(hb2.get("tasks")).isEmpty();

        JsonNode detail = json(mvc.perform(get("/api/admin/v1/terminals/" + terminalId)
                .with(httpBasic("admin", "admin123"))), 200);
        assertThat(detail.get("manufacturer").asText()).isEqualTo("PAX");
        assertThat(detail.get("online").asBoolean()).isTrue();
        assertThat(detail.get("batteryLevel").asInt()).isEqualTo(87);
        assertThat(detail.get("installedApps").get(0).get("packageName").asText()).isEqualTo(pkg);
    }

    @Test
    void uploadApkThenDeployInstallAndDownload() throws Exception {
        String token = enroll("SUNMI-0001", "SUNMI", "P2").get("deviceToken").asText();
        long terminalId = enroll("NEWLAND-0001", "Newland", "N910").get("terminalId").asLong();

        byte[] apk = "not-a-real-apk".getBytes();
        JsonNode app = json(mvc.perform(multipart("/api/admin/v1/apps")
                .file(new MockMultipartFile("file", "demo.apk", "application/octet-stream", apk))
                .param("packageName", "com.acme.demo")
                .param("versionName", "2.0.0")
                .param("versionCode", "20")
                .with(httpBasic("admin", "admin123"))), 201);
        long appId = app.get("id").asLong();

        JsonNode deployment = json(admin(post("/api/admin/v1/deployments"),
                "{\"type\":\"INSTALL_APP\",\"appId\":" + appId + ",\"target\":{\"manufacturer\":\"SUNMI\"}}"), 201);
        assertThat(deployment.get("taskCount").asInt()).isEqualTo(1);

        JsonNode task = json(device(post("/api/device/v1/heartbeat"), token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"), 200).get("tasks").get(0);
        assertThat(task.get("type").asText()).isEqualTo("INSTALL_APP");
        String url = task.get("payload").get("downloadUrl").asText();

        byte[] downloaded = mvc.perform(device(get(url), token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Checksum-SHA256", app.get("sha256").asText()))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(apk);

        // Le terminal Newland n'est pas ciblé par un déploiement SUNMI
        JsonNode newlandTasks = json(mvc.perform(get("/api/admin/v1/tasks?terminalId=" + terminalId)
                .with(httpBasic("admin", "admin123"))), 200);
        assertThat(newlandTasks).isEmpty();
    }

    @Test
    void manifestWinsOverManualFieldsOnUpload() throws Exception {
        // APK minimal réel (compilé par le build Android) ; ignoré s'il n'a pas été construit
        java.nio.file.Path apk = java.nio.file.Path.of("../android-agent/app/build/outputs/apk/debug/app-debug.apk");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.exists(apk), "APK agent non construit");

        JsonNode app = json(mvc.perform(multipart("/api/admin/v1/apps")
                .file(new MockMultipartFile("file", "agent.apk", "application/octet-stream",
                        java.nio.file.Files.readAllBytes(apk)))
                .param("packageName", "egate")
                .param("versionName", "egate")
                .with(httpBasic("admin", "admin123"))), 201);
        assertThat(app.get("packageName").asText()).isEqualTo("com.tms.agent");
        assertThat(app.get("versionName").asText()).isEqualTo("1.0.0");
    }

    @Test
    void securityRules() throws Exception {
        mvc.perform(post("/api/device/v1/enroll").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serialNumber\":\"X1\",\"enrollmentKey\":\"WRONG\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/v1/terminals")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/v1/terminals").with(httpBasic("admin", "bad"))).andExpect(status().isUnauthorized());

        // Terminal désactivé : jeton refusé et ré-enrôlement bloqué
        JsonNode enrolled = enroll("PAX-DISABLED", "PAX", "A80");
        String token = enrolled.get("deviceToken").asText();
        admin(put("/api/admin/v1/terminals/" + enrolled.get("terminalId").asLong()), "{\"status\":\"DISABLED\"}", 200);
        mvc.perform(device(post("/api/device/v1/heartbeat"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/device/v1/enroll").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serialNumber\":\"PAX-DISABLED\",\"enrollmentKey\":\"TEST-KEY\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private JsonNode enroll(String serial, String manufacturer, String model) throws Exception {
        return json(mvc.perform(post("/api/device/v1/enroll").contentType(MediaType.APPLICATION_JSON)
                .content("{\"serialNumber\":\"" + serial + "\",\"enrollmentKey\":\"TEST-KEY\",\"manufacturer\":\""
                        + manufacturer + "\",\"model\":\"" + model + "\",\"agentVersion\":\"1.0.0\"}")), 200);
    }

    private MockHttpServletRequestBuilder device(MockHttpServletRequestBuilder req, String token) {
        return req.header("X-Device-Token", token);
    }

    private org.springframework.test.web.servlet.ResultActions admin(MockHttpServletRequestBuilder req, String body)
            throws Exception {
        return mvc.perform(req.with(httpBasic("admin", "admin123"))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void admin(MockHttpServletRequestBuilder req, String body, int expected) throws Exception {
        admin(req, body).andExpect(status().is(expected));
    }

    private JsonNode json(MockHttpServletRequestBuilder req, int expected) throws Exception {
        return json(mvc.perform(req), expected);
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions actions, int expected) throws Exception {
        String body = actions.andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return om.readTree(body);
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return json(actions, 200);
    }
}
