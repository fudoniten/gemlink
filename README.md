# Gemini Server

This project is a simple server for the Gemini protocol, written in Clojure. The Gemini protocol is a lightweight, text-based protocol designed for serving content over the internet, similar to HTTP but much simpler and more privacy-focused.

## Inspiration

This project was largely inspired by Ring, a Clojure web applications library. Ring's simplicity and middleware-based architecture influenced the design of this Gemini server.

## Features

- **Gemtext Support**: The server uses a Hiccup-like language called Gemtext for generating Gemini-formatted text files. This allows for easy and expressive creation of content that can be served over the Gemini protocol.

- **Clojure-based**: Built entirely in Clojure, leveraging its functional programming capabilities and rich ecosystem.

## Getting Started

To get started with this Gemini server, you will need to have Clojure installed on your system. You can then clone this repository and run the server using the provided scripts.

## Usage

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd <repository-directory>
   ```

2. **Run the server**:
   ```bash
   clojure -M:run
   ```

3. **Access the server**: Use a Gemini client to connect to the server and view the content.

## Contributing

Contributions are welcome! Please feel free to submit a pull request or open an issue if you have any suggestions or improvements.

## License

This project is licensed under the MIT License. See the LICENSE file for more details.
