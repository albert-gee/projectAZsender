# Sender

## Overview

The Sender is a Java network application in "Project AZ". It reads data from the keyboard and sends it to the Receiver through a UDP socket:

1. Establish connection by sending a SYN packet and receiving a SYN-ACK packet. If the sender does not get the SYN-ACK packet, it tries to establish a new connection up to a specified number of times.

- The SYN packet contains the initial sequence number, the length of the entire data message to be sent, its mime type, and a checksum. If the mime type is “textstring”, the sender will print it in the console, otherwise, save it into a file of a particular type.

- The SYN-ACK packet must have the same sequence number and length to continue sending data packets.

2. Split the data into a set of AZRP data packets, send them, and expect to receive ACK packets. The data packets have a sequence number of the specific chunk of data, its length, and its checksum.

- The ACK packet must have the sequence number equal to the sequence number of the data packet plus the length of the data of the data packet.

- If the sender does not get the ACK packet for a data packet, it resends the packet.

- If the sender receives another SYN-ACK packet, it resets the data transfer session and starts receiving data packets again.

3. The data transfer ends when all data packets are sent and acknowledged.


Sender takes command line arguments for the IP address of the proxy (or receiver if the proxy is removed) and the port:

```bash
./sender -a fe80::fe80:fe80:fe80:fe80 -p 60001
```

## Components

The application consists of several classes to handle different aspects of the communication process: `AZRP`, `Main`, `DataTransfer`, and `Sender`.

### AZRP Class

Implements the AZRP protocol and represents an AZRP packet. It provides methods for packet serialization and deserialization, calculates checksums for data integrity verification, and generates SYN and SYN-ACK packets.

It uses the CRC32 algorithm to calculate the checksum for data integrity verification.

The `toBytes` method serializes an AZRP object into a byte array, and the `fromBytes` method deserializes a byte array back into an AZRP object.

The class provides methods to generate SYN and SYN-ACK packets, including the initialization of sequence numbers and flags.

The class includes a method (`isChecksumValid`) to validate the checksum of received data.

### Main Class

Parses command line arguments using Apache Commons CLI, launches the Sender application with specified receiver address and port, and handles user input.

### DataTransfer Class

The `DataTransfer` class is responsible for managing the reliable transfer of data from the sender to the receiver using the AZRP (AZRP reliable protocol) protocol. It encapsulates the logic for handling the various stages of communication, including sending SYN packets, receiving SYN-ACK packets, and sending data packets along with receiving ACK packets.

**Key Components:**
- `send`: Initiates the data transfer process, including SYN and data packet transmission.
- `receiveSynAck`: Receives SYN-ACK packets and validates them.
- `sendData`: Sends data packets and waits for ACKs.
- `convertMessageToAzrps`: Splits the message into AZRP data packets.

### Sender Class

Implements an abstraction for the communication with the receiver by utilizing the `DataTransfer` class.

**Key Components:**
- `sendUserInput`: Determines message type and delegates to `DataTransfer`.
- `writeStatistics`: Appends statistics to a log file.

  

# "Project AZ" 

## Overview

"Project AZ" is an implementation of a custom reliable data transfer protocol named **AZRP**. This protocol is built on top of UDP and designed to provide reliability and efficient communication in diverse network environments.

The project comprises four key applications:

- **[Sender](https://github.com/albert-gee/projectAZsender):** The initiator of data transmission, responsible for sending packets using the AZRP protocol.
- **[Receiver](https://github.com/albert-gee/projectAZreceiver):** Responsible for receiving packets transmitted using the AZRP protocol.
- **[Proxy](https://github.com/albert-gee/projectAZproxy):** Acts as an intermediary between the Sender and Receiver, simulating a lossy network environment.
- **[GUI](https://github.com/albert-gee/projectAZproxy):** Provides a user-friendly interface for interacting with the AZRP protocol.

The project is compatible with both IPv4 and IPv6, ensuring that it can work in different network environments.

## AZRP Protocol

**AZRP** is built on top of UDP, leveraging its simplicity and low overhead. The integration ensures compatibility with existing UDP-based applications while enhancing reliability. By addressing the challenges inherent in UDP and incorporating sophisticated error recovery mechanisms, AZRP aims to provide a dependable solution for data transmission in diverse network environments.

### Packet Structure

Each packet consists of a header and a data payload. The header includes four integer fields:

- Sequence number
- Length
- Checksum
- Flags representing packet type (e.g., SYN, ACK)

The total size of the packet header is 19 bytes, and the maximum packet size is 1500 bytes.

### Packet Types

Flags in the header indicate the type of packet (SYN, ACK). The protocol supports four types of packets:

- **Data:** Used for transmitting data (both flags are false).
- **SYN (Synchronise):** Used for initiating a connection. Includes the initial sequence number, the length of the whole message to be sent, and the file extension in the data payload.
- **SYN-ACK (Synchronise-Acknowledge):** Used to acknowledge a SYN packet. It must include the same sequence number, length, checksum, and the file extension as the corresponding SYN packet.
- **ACK (Acknowledgement):** Used to acknowledge receiving a data packet. Must include the same sequence number and checksum as the corresponding data packet.

### Checksum Calculation

The checksum of the packet's data is calculated using the CRC32 algorithm. It is used for error detection, ensuring data integrity during transmission.

### Security

The `generateInitialSequenceNumber` method in the AZRP protocol ensures the secure creation of an initial sequence number for SYN packets. It employs the `SecureRandom` class to generate cryptographically secure random bytes, which are then combined to form a non-negative integer. This secure sequence number is vital for the AZRP protocol, enhancing the unpredictability and resistance to attacks during the establishment of connections, contributing to the overall security and reliability of the communication channel.

### Example of Usage

```java
// Creating a data packet
AZRP dataPacket = AZRP.data("Hello, World!".getBytes(), 1, 13);

// Serializing the packet to bytes
byte[] serializedData = dataPacket.toBytes();

// Deserializing the bytes back into an AZRP object
AZRP receivedPacket = AZRP.fromBytes(serializedData);
```
