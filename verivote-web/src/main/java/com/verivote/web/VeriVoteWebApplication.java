package com.verivote.web;

import com.verivote.core.ledger.FileLedgerService;
import com.verivote.core.ledger.LedgerService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication(scanBasePackages = {"com.verivote"})
@EntityScan(basePackages = {"com.verivote.data.entity"})
@EnableJpaRepositories(basePackages = {"com.verivote.data.repo"})
public class VeriVoteWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(VeriVoteWebApplication.class, args);
    }

    @Bean
    public LedgerService ledgerService() {
        Path p = Paths.get(System.getProperty("verivote.ledger.path", "./ledger/blocks.ndjson"));
        return new FileLedgerService(p);
    }
}
