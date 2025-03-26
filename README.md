# 🚀 DepChain

This repository implements the **DepChain**, a high-dependability blockchain system.

---

## ⚡ **Installation**

This project requires **Java 21** and **Maven**. Install dependencies and compile:

```sh
# Clone the repository
git clone https://github.com/andremmsilva/sec-DepChain-2425.git
cd sec-DepChain-2425

# Build the project
mvn clean install
```

---

## 🧪 **Running Tests**

To verify that each layer functions correctly, run:

```sh
mvn test
```

## **✅ Test Suite Overview**

The tests use JUnit5 and are written in the `src/test/java/depchain/**` directories.

An overview of the suite can be found [here](common/src/test/tests.md).

## 📜 **Code Overview**

The project's source code is contained within the `src/main/java/depchain/**` directories.

Each subdirectory corresponds to one of the packages listed below:

### **📂 `depchain.config` - Node Management and Global Configurations**

### **📂 `depchain.events` - Event Management**

#### 🔹 `Event.java`

> Abstract Class describing "something happening". **Contains an argument (`arg`)of any type.**

#### 🔹 `EventBus.java`

> Manages the registration and unregistration of event consumers. Additionally, manages the delivery of events to these consumers asynchronously.

### **📂 `depchain.network` - Networking Stack**

#### 🔹 `Message.java`

> Represents a message exchanged across the network. **Contains senderId, payload and possibly a MAC and DS**.

#### 🔹 `FairLossLink.java`

> Basic UDP communication. **Messages can be lost, duplicated, or reordered**.

#### 🔹 `StubbornLink.java`

> Ensures **eventual delivery** by **retransmitting messages**.

#### 🔹 `AuthenticatedPerfectLink.java`

> Adds **cryptographic authentication** to prevent **spoofing and duplication**.

### **📂 `depchain.crypto` - Cryptography**

#### 🔹 `CryptoUtils.java`

> Provides **RSA digital signatures and MACs** for authentication, as well as key generation.
