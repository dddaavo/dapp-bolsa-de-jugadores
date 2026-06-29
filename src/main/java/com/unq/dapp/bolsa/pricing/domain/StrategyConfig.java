package com.unq.dapp.bolsa.pricing.domain;

import com.unq.dapp.bolsa.shared.audit.AuditableEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "strategy_config")
public class StrategyConfig extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String name;

    private boolean active = true;

    private int configVersion = 0;

    @Column(columnDefinition = "TEXT")
    private String weightsJson;

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getConfigVersion() { return configVersion; }
    public void setConfigVersion(int configVersion) { this.configVersion = configVersion; }

    public String getWeightsJson() { return weightsJson; }
    public void setWeightsJson(String weightsJson) { this.weightsJson = weightsJson; }
}
