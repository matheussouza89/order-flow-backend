db.getSiblingDB('orderflow').createUser({
  user: 'sales',
  pwd: process.env.MONGO_SALES_PASSWORD,
  roles: [{ role: 'readWrite', db: 'orderflow' }]
});
