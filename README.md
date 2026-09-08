# xmlFastParser & Xml2Txt

[![Java Version](https://img.shields.io/badge/Java-8%2B-blue.svg)](https://www.java.com)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Linux%20%7C%20Windows%20%7C%20macOS-lightgrey.svg)]()

**xmlFastParser** is a high-performance, open-source, multithreaded Java XML parsing library designed for large-scale data processing and high-throughput streaming. By combining a **Finite State Machine (FSM)** for rapid syntax processing with a **TreeGraph** data structure for navigating node hierarchies, it efficiently locates target XML tags and triggers user-defined callback handlers upon discovery.

In addition to standard parsing, `xmlFastParser` features **Statistical Speculative Navigation**—it gathers runtime structural metrics to predict upcoming tag locations and jump directly to relevant data pointers without scanning every character line-by-line. It is also inherently **Fault-Tolerant**, allowing parsing to proceed seamlessly despite minor or non-critical XML syntax errors as long as the target data path remains traversable.

---

## Key Features

* **FSM-Based Syntax Engine:** Ultra-fast, low-overhead XML tokenization driven by a custom Finite State Machine.
* **TreeGraph Navigation:** Leverages a TreeGraph structure to maintain node hierarchies and accurately resolve target tags.
* **Statistical Speculative Skipping:** Collects runtime metrics to predict future target offsets, skipping irrelevant XML streams instantly.
* **Fault-Tolerant & Resilient:** Ignores non-critical malformed XML syntax to ensure continuous processing during large-scale data ingestion.
* **Callback Handler System:** Triggers custom user handlers immediately upon matching targeted tags.
* **ZIP Archive Extraction:** Supports extracting and parsing XML streams directly from compressed archives in memory.

---

## Repository Structure

```text
.
└── java/
    ├── xmlFastParser/     # Core multithreaded Java XML parsing library
    └── Xml2Txt/           # Example application for XML-to-TXT/CSV conversion & ZIP processing
java/xmlFastParser/: The core engine containing the FSM parser, TreeGraph structure, speculative algorithms, and callback interfaces.

java/Xml2Txt/: An example application built on top of xmlFastParser. It reads configuration files (.bp) and converts raw or ZIP-compressed XML data into a CSV-like .txt format optimized for database ingestion.

Performance Benchmark
The following benchmark demonstrates single-threaded processing performance using the Xml2Txt example application:

Dataset: 5 GB ZIP archive (containing 10 GB uncompressed XML across ~1,000,000 XML files)

Output: 1.7 GB converted .txt file (database-ready format)

Execution Time: 165 seconds (Single-threaded execution)

Test Environment
Guest VM: Lubuntu Linux (Allocated: 3 vCPUs, 8 GB RAM)

Host Machine: VMware on Windows 8

Hardware: Intel Core i5 CPU, Western Digital Blue 1TB HDD (WD10EZEX, 7200 RPM, 64MB Cache)

Getting Started
Prerequisites
Java Development Kit (JDK) 8 or higher

Quick Usage Example
Java
import xmlFastParser.XmlFastReader;
import xmlFastParser.handler.ElementHandler;

public class Main {
    public static void main(String[] args) {
        // Create parser
        parser = new xmlBluePrint();

        // Tune parser
        parser.setPipeLineDeep(C_PipeLineDeep);

        // Set thread count
        parser.setThreadCount(numThreads);
        
        // Register handlers with parser
        parser.rootRegist(EventHandlers.HD_Root, tokenRoot);
        parser.errorRegist(EventHandlers.HD_Error, tokenRoot);
        parser.regist(strXmlPath_1, EventHandler_1, objToken_1);
        parser.regist(strXmlPath_2, EventHandler_2, objToken_2);
        parser.regist(strXmlPath_3, EventHandler_3, objToken_3);
        .
        .
        parser.regist(strXmlPath_N, EventHandler_N, objToken_N);

        // Parse an XML file or stream
        parser.run();

        // << one to many threads interface >>
        // use pushJob and pullJob to easy comunicate to multitheads parser

        // push job to parser 
        parser.pushJob(xml.byteBuffer, xml.size, xmlToken);

        // Wait for completed job
        while ((xmlToken = (XmlToken) parser.pullJob()) == null) {
            LockSupport.parkNanos(200_000L); // 0.2 ms
        }

        // Flush data to output files
        flushXmlToken(xmlToken);
      
    }
}
Contributing
Contributions, issues, and feature requests are welcome! Feel free to check the issues page if you want to contribute to the core library or improve performance further.

License
This project is open-source software licensed under the MIT License. Free for commercial and non-commercial use, modification, and distribution.
