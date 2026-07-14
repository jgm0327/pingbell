package com.monit.pingbell.loganalysis.dto;

import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

public class LogAnalysisRequest {

    private List<MultipartFile> file = new ArrayList<>();
    private String question;

    public List<MultipartFile> getFile() {
        return file;
    }

    public void setFile(List<MultipartFile> file) {
        this.file = file;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
