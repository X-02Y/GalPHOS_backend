// Test script to verify microservice routing
import { microserviceRouter } from './src/services/microserviceRouter.js';

// Test the routing for answer image upload
const testPath = '/api/student/upload/answer-image';
const routedService = microserviceRouter.routeRequest(testPath);
const fullUrl = microserviceRouter.buildApiUrl(testPath);

console.log('Testing path:', testPath);
console.log('Routed to service:', routedService.name);
console.log('Service port:', routedService.port);
console.log('Full URL:', fullUrl);
console.log('Expected: http://localhost:3004/api/student/upload/answer-image');

// Also test the inference logic directly
const pathMatch = microserviceRouter.getMatchingServiceInfo(testPath);
if (pathMatch) {
  console.log('Matched service:', pathMatch.serviceName);
  console.log('Service config:', pathMatch.config.name, 'on port', pathMatch.config.port);
} else {
  console.log('No exact match found, using inference');
}
