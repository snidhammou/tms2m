package com.tms.server.web.admin;

import com.tms.server.domain.Merchant;
import com.tms.server.domain.TerminalGroup;
import com.tms.server.repository.MerchantRepository;
import com.tms.server.repository.TerminalGroupRepository;
import com.tms.server.repository.TerminalRepository;
import com.tms.server.service.ApiException;
import com.tms.server.web.dto.AdminDtos.GroupDto;
import com.tms.server.web.dto.AdminDtos.MerchantDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** CRUD des marchands et des groupes de terminaux. */
@RestController
@RequestMapping("/api/admin/v1")
public class ReferenceDataController {

    private final MerchantRepository merchants;
    private final TerminalGroupRepository groups;
    private final TerminalRepository terminals;

    public ReferenceDataController(MerchantRepository merchants, TerminalGroupRepository groups,
                                   TerminalRepository terminals) {
        this.merchants = merchants;
        this.groups = groups;
        this.terminals = terminals;
    }

    // ---- Marchands ----

    @GetMapping("/merchants")
    public List<MerchantDto> merchants() {
        return merchants.findAll(Sort.by("name")).stream().map(MerchantDto::of).toList();
    }

    @PostMapping("/merchants")
    @ResponseStatus(HttpStatus.CREATED)
    public MerchantDto createMerchant(@Valid @RequestBody MerchantDto dto) {
        return MerchantDto.of(merchants.save(apply(new Merchant(), dto)));
    }

    @PutMapping("/merchants/{id}")
    @Transactional
    public MerchantDto updateMerchant(@PathVariable Long id, @Valid @RequestBody MerchantDto dto) {
        Merchant m = merchants.findById(id).orElseThrow(() -> ApiException.notFound("Marchand", id));
        return MerchantDto.of(merchants.save(apply(m, dto)));
    }

    @DeleteMapping("/merchants/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMerchant(@PathVariable Long id) {
        if (terminals.existsByMerchantId(id)) {
            throw ApiException.conflict("Des terminaux sont rattachés à ce marchand");
        }
        merchants.deleteById(id);
    }

    // ---- Groupes ----

    @GetMapping("/groups")
    public List<GroupDto> groups() {
        return groups.findAll(Sort.by("name")).stream().map(GroupDto::of).toList();
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDto createGroup(@Valid @RequestBody GroupDto dto) {
        TerminalGroup g = new TerminalGroup();
        g.setName(dto.name().trim());
        g.setDescription(dto.description());
        return GroupDto.of(groups.save(g));
    }

    @PutMapping("/groups/{id}")
    @Transactional
    public GroupDto updateGroup(@PathVariable Long id, @Valid @RequestBody GroupDto dto) {
        TerminalGroup g = groups.findById(id).orElseThrow(() -> ApiException.notFound("Groupe", id));
        g.setName(dto.name().trim());
        g.setDescription(dto.description());
        return GroupDto.of(groups.save(g));
    }

    @DeleteMapping("/groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(@PathVariable Long id) {
        if (terminals.existsByGroupId(id)) {
            throw ApiException.conflict("Des terminaux sont rattachés à ce groupe");
        }
        groups.deleteById(id);
    }

    private static Merchant apply(Merchant m, MerchantDto dto) {
        m.setCode(dto.code().trim());
        m.setName(dto.name().trim());
        m.setCity(dto.city());
        m.setAddress(dto.address());
        return m;
    }
}
