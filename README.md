# DepChain

A small permissioned blockchain that keeps working when some of its nodes lie. Built in Java for the Highly Dependable Systems course at Instituto Superior Técnico (MSc, 2024/25), in a team of three.

Four nodes agree on each block with a Byzantine fault-tolerant consensus protocol, so the chain stays consistent even if one of them crashes, goes silent or sends conflicting messages on purpose. Transactions in each block run against smart contracts on the Hyperledger Besu EVM. The demo contract is an ERC-20 token with a blacklist.

## How it works

The code is layered, each layer built only on the one below it:

- **Links.** UDP fair-loss links, then stubborn links that retransmit until acknowledged and drop duplicates, then authenticated perfect links that agree on a shared key through an RSA-signed handshake and reject any message whose MAC does not check out.
- **Consensus.** Byzantine read/write epoch consensus with a conditional-collect step: the leader gathers signed state from every node, and a value is only decided once a Byzantine quorum agrees it is safe.
- **Execution.** Each decided block is run through the Besu EVM against the `ISTCoin` (ERC-20) and `Blacklist` contracts in [`node/contracts`](node/contracts). The genesis block sets the initial accounts and balances, in the native DepCoin and in IST Coin.
- **Client.** A command-line client that signs each transaction and only accepts the result once f+1 nodes confirm it, so a single lying node cannot fool it.

## Tests

The JUnit 5 suite tests each layer on its own, and tests consensus with a silent node and with a Byzantine node in the network. GitHub Actions runs it on every push. [`test.sh`](test.sh) runs the suite repeatedly to catch timing-dependent failures.

```sh
mvn clean install    # build and run the tests (Java 21, Maven)
./test.sh 20         # run the suite 20 times
```

## Running it

Start the four nodes (ids 0 to 3) in separate terminals, then a client (ids 10 to 12):

```sh
java -jar node/target/node-1.0-SNAPSHOT.jar 0
java -jar client/target/client-1.0-SNAPSHOT.jar 10
```

The client accepts `balance`, `transfer`, `transferfrom`, `addblacklist`, `removeblacklist` and `isblacklisted`.

The keys in `common/src/main/resources/keys` are test keys generated for this project. They are not used anywhere else.
