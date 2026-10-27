package com.jayadeep.jobradar.apply;

import java.util.List;

/** A made-up candidate used across the apply tests. */
final class TestProfiles {

    private TestProfiles() {
    }

    static CandidateProfile.Resume resume() {
        return new CandidateProfile.Resume(
                "Software Engineer with 4+ years building backend systems in Java and Spring Boot. Cut failure recovery time by 80%.",
                List.of(new CandidateProfile.SkillGroup("Languages", "Java, Python, Go, SQL"),
                        new CandidateProfile.SkillGroup("Backend", "Spring Boot, REST APIs, Microservices"),
                        new CandidateProfile.SkillGroup("Data", "Apache Kafka, Redis, Oracle (PL/SQL, SQL*Loader), PostgreSQL"),
                        new CandidateProfile.SkillGroup("Cloud", "AWS, Azure (AKS), Docker, Kubernetes, Terraform")),
                List.of(new CandidateProfile.Experience("Acme Federal", "Remote, USA", "Software Engineer", "Jun 2026 – Present", List.of(
                                "Develop Java/Spring Boot services and REST APIs for a federal budget system used by 30+ program offices.",
                                "Support a seven-job nightly batch pipeline that loads financial extracts over SFTP with Oracle SQL*Loader.",
                                "Enhance Oracle PL/SQL packages that validate and deduplicate incoming financial records.",
                                "Troubleshoot production batch failures using application logs, Oracle error codes and audit tables.",
                                "Tune reconciliation reports with stored procedures and materialized views.",
                                "Maintain CI/CD deployment workflows and audit-ready processing logs for controlled releases.")),
                        new CandidateProfile.Experience("Big Consulting", "Remote, USA", "Software Development Engineer", "Dec 2024 – May 2026", List.of(
                                "Split Java/Spring Boot transaction services into microservices on AKS, raising throughput by 40%.",
                                "Built Kafka and Redis pipelines for real-time payments, cutting settlement delays by 50%.",
                                "Built Temporal workflows in Go that retry transient failures with backoff, reducing recovery time by 80%.",
                                "Integrated a fraud detection model into backend APIs, reducing fraudulent transactions by 60%.",
                                "Designed SQL Server schemas and T-SQL stored procedures for reconciliation data.",
                                "Implemented RBAC and encryption to meet PCI-DSS requirements.",
                                "Wrote unit and integration tests and reviewed teammates' code.")),
                        new CandidateProfile.Experience("State University", "Denton, TX", "Graduate Research Assistant", "Jan 2024 – May 2024", List.of(
                                "Built an AI orchestration platform with LangChain and GPT-4 behind Java APIs, raising labeling throughput by 60%.",
                                "Developed a multi-threaded job scheduler on an HPC cluster, cutting job runtime by 25%.",
                                "Automated Kubernetes deployments with rollback and built Grafana dashboards.")),
                        new CandidateProfile.Experience("Data Startup", "Hyderabad, India", "Software Development Engineer", "Jun 2021 – Jun 2023", List.of(
                                "Built Kafka streaming pipelines for millions of daily transactions, reducing latency by 40%.",
                                "Developed Java/Spring Boot microservices for financial reporting on Azure.",
                                "Led incident triage and root cause analysis as the on-call owner for production systems.",
                                "Implemented blue-green and canary deployments for always-on transaction processing.",
                                "Wrote runbooks that reduced escalation time by 30%."))),
                List.of(new CandidateProfile.Project("Job Radar (Java 21, Spring Boot)", "Oct 2026",
                        List.of("Built a service that ingests job postings, screens them with rules and ranks fit with an LLM."))),
                List.of(new CandidateProfile.Education("Master of Science in Information Systems", "State University | Denton, TX", "May 2025"),
                        new CandidateProfile.Education("Bachelor of Technology in Computer Science", "Tech Institute | India", null)),
                List.of("AWS Certified Developer – Associate", "Azure Administrator (AZ-104)", "Azure AI Engineer (AI-102)", "Linux Foundation LFCS"));
    }

    static CandidateProfile profile(Boolean authorized, Boolean sponsorship) {
        return new CandidateProfile("Alex", "Example", "alex@example.com", "(555) 555-0100", "Austin", "TX", "United States",
                "https://www.linkedin.com/in/alex-example", "https://github.com/alex-example", null,
                "Acme Federal", "Software Engineer", 4, authorized, sponsorship, null, null, "2 weeks", "Company website",
                null, resume());
    }
}
