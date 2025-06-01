<h1 align="center">🌐 Distributed Hash Table (DHT)</h1>

<p align="center">
  <strong>Языки:</strong> <a href="README.md">🇺🇸 English</a> | <a href="README.ru.md">🇷🇺 Русский</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=java" alt="Java 17">
  <img src="https://img.shields.io/badge/Spring_Boot-3.0+-brightgreen?style=flat-square&logo=spring" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Redis-Compatible-red?style=flat-square&logo=redis" alt="Redis">
</p>

<p align="center">
  <strong>A high-performance, fault-tolerant distributed hash table implementation using consistent hashing and data replication.</strong>
</p>

---

## 🚀 Features

✨ **Consistent Hashing Algorithm** - Ensures even data distribution across nodes with minimal data movement during scaling  
🔄 **Automatic Data Replication** - Configurable replication factor (default: 3) for high availability and fault tolerance  
⚡ **Virtual Nodes Support** - Enhanced load balancing with configurable virtual nodes per physical node  
🎯 **Dynamic Node Management** - Add/remove nodes at runtime with automatic rebalancing  
📊 **Built-in Metrics & Monitoring** - Real-time performance metrics and health monitoring  
🔧 **Spring Boot Integration** - Easy configuration and deployment with Spring ecosystem  
🛡️ **Fault Tolerance** - Graceful handling of node failures with automatic failover  
💾 **Redis Compatibility** - Optional Redis backend for persistent storage  
🧪 **Comprehensive Testing** - Full test suite with load balancing and consistency tests  
📈 **Horizontal Scalability** - Seamlessly scale from 3 to hundreds of nodes  
🔐 **Thread-Safe Operations** - Concurrent read/write operations with proper locking  
📋 **RESTful API** - HTTP endpoints for easy integration and management

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    DHT Cluster                              │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐              │
│  │  Node 1  │◄──►│  Node 2  │◄──►│  Node 3  │              │
│  │ (Primary)│    │(Replica) │    │(Replica) │              │
│  └──────────┘    └──────────┘    └──────────┘              │
│           ▲              ▲              ▲                   │
│           │              │              │                   │
│      ┌────┴──────────────┴──────────────┴─────┐             │
│      │        Consistent Hash Ring            │             │
│      │    (Virtual Nodes Distribution)        │             │
│      └────────────────────────────────────────┘             │
│                           ▲                                 │
│      ┌────────────────────┴─────────────────────┐           │
│      │         DHT Controller                   │           │
│      │    (Load Balancing & Replication)        │           │
│      └──────────────────────────────────────────┘           │
└─────────────────────────────────────────────────────────────┘
```

---

## 📦 Installation

### Prerequisites

- **Java 17+** ☕
- **Maven 3.6+** 📦
- **Redis** (optional, for persistence) 🗄️

### Quick Start

```bash
# Clone the repository
git clone https://github.com/yourusername/distributed-hash-table.git
cd distributed-hash-table

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

<!-- ### Docker Setup

```bash
# Build Docker image
docker build -t dht-node .

# Run with Docker Compose
docker-compose up -d
``` -->

### Configuration

Edit `application.yml`:

```yaml
dht:
  replication-factor: 3          # Number of replicas per key
  virtual-nodes: 150             # Virtual nodes per physical node
  initial-nodes:                 # Bootstrap nodes
    - id: "node1"
      host: "localhost"
      port: 8001
    - id: "node2"
      host: "localhost"
      port: 8002

spring:

    (other configurations)

    data:
        redis:
            host: localhost
            port: 6379
            timeout: 2000ms
```

---

## 💻 API Usage

### Basic Operations

```bash
# Store a key-value pair
curl -X POST "http://localhost:8080/api/dht/put?key=user123&value=john_doe"

# Retrieve a value
curl -X GET "http://localhost:8080/api/dht/get?key=user123"

# Delete a key
curl -X DELETE "http://localhost:8080/api/dht/delete?key=user123"
```

### Node Management

```bash
# Add a new node
curl -X POST "http://localhost:8080/api/dht/nodes" \
  -H "Content-Type: application/json" \
  -d '{"id":"node4","host":"localhost","port":8004}'

# Remove a node
curl -X DELETE "http://localhost:8080/api/dht/nodes/node4"

# List all nodes
curl -X GET "http://localhost:8080/api/dht/nodes"
```

### Monitoring & Metrics

```bash
# Get data distribution
curl -X GET "http://localhost:8080/api/dht/distribution"

# Health check
curl -X GET "http://localhost:8080/api/dht/health"

# Performance metrics
curl -X GET "http://localhost:8080/actuator/metrics"
```

---

## 🧪 Testing

### Run All Tests

```bash
mvn test
```

### Spot Testing

```bash
# Run load balancing tests
mvn test -Dtest=FailureSimulationTest
```

---

## 📊 Performance Characteristics

| Metric            | Value           | Notes                   |
| ----------------- | --------------- | ----------------------- |
| **Throughput**    | ~50,000 ops/sec | Single node, in-memory  |
| **Latency (P99)** | < 5ms           | Local network           |
| **Replication**   | Configurable    | Default: 3 replicas     |
| **Consistency**   | Eventual        | With quorum reads       |
| **Availability**  | 99.9%+          | With proper replication |
| **Scalability**   | Linear          | Up to 100+ nodes tested |

---

## 🔧 Advanced Usage

### Java Client

```java
@Autowired
private DistributedHashTable dht;

// Store data
dht.put("session:123", "user_data");

// Retrieve data
String userData = dht.get("session:123");

// Remove data
boolean removed = dht.remove("session:123");

// Check system health
DHTMetrics metrics = dht.getMetrics();
Map<String, Integer> distribution = dht.getDataDistribution();
```

### Custom Node Implementation

```java
@Component
public class CustomNode extends Node {
    public CustomNode(String id, String host, int port) {
        super(id, host, port);
    }

    @Override
    protected void onDataReceived(String key, String value) {
        // Custom logic for data processing
        logger.info("Received: {} -> {}", key, value);
    }
}
```

---

## ⚡️ Troubleshooting

### Common Issues

**❌ Problem:** `No active nodes available`  
**✅ Solution:** Ensure at least one node is running and registered:

```bash
curl -X GET "http://localhost:8080/api/dht/nodes"
```

## 📈 Monitoring

### Built-in Metrics

```bash
# Node health
GET /api/dht/health

# Data distribution
GET /api/dht/distribution

# Performance metrics
GET /actuator/metrics
```

---

