// Debug test to check routing
const testPath = '/api/student/upload/answer-image';

// Import the router to test
import { microserviceRouter } from './src/services/microserviceRouter.js';

console.log('Testing microservice routing for answer image upload...');
console.log('Path:', testPath);

try {
  const service = microserviceRouter.routeRequest(testPath);
  console.log('Service selected:', service.name);
  console.log('Service port:', service.port);
  console.log('Service baseUrl:', service.baseUrl);
  
  const fullUrl = microserviceRouter.buildApiUrl(testPath);
  console.log('Generated URL:', fullUrl);
  console.log('Expected URL: http://localhost:3004/api/student/upload/answer-image');
  
  if (fullUrl.includes(':3004')) {
    console.log('✅ SUCCESS: URL is correctly routing to port 3004');
  } else {
    console.log('❌ FAILURE: URL is not routing to the correct port');
    console.log('URL contains port:', fullUrl.match(/:(\d+)/)?.[1] || 'no port found');
  }
} catch (error) {
  console.error('Error during routing test:', error);
}
