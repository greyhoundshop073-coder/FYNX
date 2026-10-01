// Load production infrastructure layers before the realtime/server bootstrap.
// Node executes --import modules before the application entrypoint.
import "./scalability.js";
import "./chatRealtimeCompatibility.js";
