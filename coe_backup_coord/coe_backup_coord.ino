void setup() {
  Serial.begin(115200);
  Serial.println("Arduino Ready - Waiting for data on RX pin");

  delay(5000);
  Serial.println("start");

}

String receivedMessage = ""; // Buffer to hold the incoming message

void loop() {
  // Check if data is available on RX
  while (Serial.available() > 0) {
    // Read the next character
    char receivedChar = Serial.read();

    if (receivedChar == '\n') {
      // Process the complete message
      Serial.print("Received Message: ");
      Serial.println(receivedMessage);

      receivedMessage = "";
    } else {
      // Append character to the message buffer
      receivedMessage += receivedChar;
    }
  }

  delay(100); // Delay to prevent flooding
}
