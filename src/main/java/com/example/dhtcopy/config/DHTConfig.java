package com.example.dhtcopy.config;


import com.example.dhtcopy.core.Node;
import com.example.dhtcopy.core.DistributedHashTable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.List;

@Configuration
@EnableAsync
public class DHTConfig {

    @Autowired
    private DistributedHashTable distributedHashTable;

    @ConfigurationProperties(prefix = "dht")
    public static class DHTProperties {
        private int replicationFactor = 3;
        private int virtualNodes = 150;
        private List<NodeConfig> initialNodes;

        public static class NodeConfig {
            private String id;
            private String host;
            private int port;

            // Getters and setters
            public String getId() { return id; }
            public void setId(String id) { this.id = id; }

            public String getHost() { return host; }
            public void setHost(String host) { this.host = host; }

            public int getPort() { return port; }
            public void setPort(int port) { this.port = port; }
        }

        // Getters and setters
        public int getReplicationFactor() { return replicationFactor; }
        public void setReplicationFactor(int replicationFactor) { this.replicationFactor = replicationFactor; }

        public int getVirtualNodes() { return virtualNodes; }
        public void setVirtualNodes(int virtualNodes) { this.virtualNodes = virtualNodes; }

        public List<NodeConfig> getInitialNodes() { return initialNodes; }
        public void setInitialNodes(List<NodeConfig> initialNodes) { this.initialNodes = initialNodes; }
    }

    @Bean
    @ConfigurationProperties(prefix = "dht")
    public DHTProperties dhtProperties() {
        return new DHTProperties();
    }

    @Bean
    public void initializeNodes() {
        DHTProperties properties = dhtProperties();
        if (properties.getInitialNodes() != null) {
            for (DHTProperties.NodeConfig nodeConfig : properties.getInitialNodes()) {
                Node node = new Node(nodeConfig.getId(), nodeConfig.getHost(), nodeConfig.getPort());
                distributedHashTable.addNode(node);
            }
        }
    }
}

