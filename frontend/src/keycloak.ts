import Keycloak from 'keycloak-js';

const keycloak = new Keycloak({
  url: process.env.REACT_APP_KEYCLOAK_URL || 'http://localhost:9090',
  realm: process.env.REACT_APP_KEYCLOAK_REALM || 'esignpayment',
  clientId: process.env.REACT_APP_KEYCLOAK_CLIENT_ID || 'esignpay-frontend',
});

export default keycloak;
