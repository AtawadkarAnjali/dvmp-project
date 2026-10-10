package com.devops.dvmp;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    String tooBig(RedirectAttributes ra) {
        ra.addFlashAttribute("error", "File is too large (maximum 100 MB).");
        return "redirect:/";
    }
}
