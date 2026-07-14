package com.monit.pingbell.loganalysis.service;

record ProcessedLog(String content, boolean truncated, int analyzedCharacters) {
}
