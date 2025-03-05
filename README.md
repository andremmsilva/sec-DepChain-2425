# 🚀 DepChain

This repository implements the **DepChain**, a high-dependability blockchain system.

---

## ⚡ **Installation**

This project requires **Java 11+** and **Maven**. Install dependencies and compile:

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

### **✅ Test Suite Overview**

The tests use JUnit5 and are written in the `src/test/java/depchain/**` directories.

| **Test**                                      | **Checks That...**                  | **Expected Behavior**        |
|-----------------------------------------------|-------------------------------------|------------------------------|
| `testFairLossLinkMessageLoss()`               | **FLL drops messages randomly**     | Some messages are lost       |
| `testFairLossLinkMessageDuplication()`        | **FLL may duplicate messages**      | Same message received twice  |
| `testFairLossLinkMessageReordering()`         | **FLL may reorder messages**        | Messages arrive out of order |
| `testStubbornLinkEnsuresDelivery()`           | **SL eventually delivers messages** | Message always arrives       |
| `testAuthenticatedPerfectLinkNoDuplication()` | **APL delivers each message once**  | No duplicate messages        |
| `testAuthenticatedPerfectLinkAuthenticity()`  | **APL rejects forged messages**     | Fake messages are ignored    |

## 📜 **Code Overview**

The project's source code is contained within the `src/main/java/depchain/**` directories.

Each subdirectory corresponds to one of the packages listed below:

### **📂 `depchain.network` - Networking Stack**

#### 🔹 `FairLossLink.java`

> Basic UDP communication. **Messages can be lost, duplicated, or reordered**.

#### 🔹 `StubbornLink.java`

> Ensures **eventual delivery** by **retransmitting messages**.

#### 🔹 `AuthenticatedPerfectLink.java`

> Adds **cryptographic authentication** to prevent **spoofing and duplication**.

### **📂 `depchain.crypto` - Cryptography**

#### 🔹 `CryptoUtils.java`

> Provides **RSA digital signatures** for authentication.
