// Load the production scalability layer before the realtime/server bootstrap.
// Node executes --import modules before the application entrypoint.
import "./scalability.js";
