# graphql-kotlin-spring-server
An application that simulate the Swedish Transport Agency, implemented with Spring Boot, Kotlin and GraphQL

## Running the server
```./gradlew bootRun```

## Client
http://localhost:8080/playground

### Example queries and mutations

All cars:
```graphql
query QueryAllCars {
  cars {
    id
    brand
    model
    owners {
      name
    }
  }
}
```

Generate license plate:
```graphql
query GenerateLicensePlate {
  generateLicensePlate {
    value
  }
}
```

Add a new car:
```graphql
mutation AddCar {
  addCar(carInput: {
    brand: "Opel"
    model: "Ascona"
  }
 ) {
    id
    brand
    model
  }
}
```