package ru.syntezis.cronctl.presentation.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import ru.syntezis.cronctl.properties.CronctlProperties;

/** Serves the optional cronctl operator UI. */
@Controller
@Hidden
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.thymeleaf.spring6.SpringTemplateEngine")
@RequestMapping("${cronctl.api.base-path}/ui")
public class CronctlUiController {

    private final CronctlProperties properties;

    @GetMapping({"", "/"})
    public String getUi(Model model) {
        if (!properties.getUi().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        model.addAttribute("apiBasePath", properties.getApi().getBasePath());
        return "cronctl/index";
    }
}
