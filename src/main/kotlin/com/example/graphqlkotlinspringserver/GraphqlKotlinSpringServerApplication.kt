package com.example.graphqlkotlinspringserver

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.generator.hooks.SchemaGeneratorHooks
import com.expediagroup.graphql.generator.scalars.ID
import com.expediagroup.graphql.server.operations.Mutation
import com.expediagroup.graphql.server.operations.Query
import graphql.language.StringValue
import graphql.scalars.datetime.DateScalar
import graphql.scalars.datetime.DateTimeScalar
import graphql.schema.Coercing
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Random
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.streams.asSequence

@SpringBootApplication
class GraphqlKotlinSpringServerApplication

fun main(args: Array<String>) {
    runApplication<GraphqlKotlinSpringServerApplication>(*args)
}

data class Person(
    val id: ID,
    val name: String
)

val persons = listOf(
    Person(ID("1"), "Keith Richards"),
    Person(ID("2"), "Steven Tyler"),
    Person(ID("3"), "Samantha Fox"),
    Person(ID("4"), "Bonnie Raitt"),
)

@Suppress("unused")
data class Car(
    // TODO petves: Ändra typen för id till LicensePlate?
    val id: ID,
    val brand: String,
    val model: String
) {
    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    // Denna anropas enbart om man anger att man vill ha datat i GraphQL-queryn
    @Suppress("RedundantSuspendModifier")
    suspend fun owners(): List<Person> {
        logger.info("Fetching owners for car $this")
        return persons.shuffled().take(2)
    }
}

data class UUIDThing(
    val uuid: UUID,
    val name: String,
    val date: LocalDate,
    val dateTime: OffsetDateTime
)

@Configuration
class FooConfig {

    @Bean
    fun schemaGeneratorHooks(): SchemaGeneratorHooks {
        return object : SchemaGeneratorHooks {
            override fun willGenerateGraphQLType(type: KType): GraphQLType? = when (type.classifier as? KClass<*>) {
                UUID::class -> graphqlUUIDType
                LocalDate::class -> DateScalar.INSTANCE
                OffsetDateTime::class -> DateTimeScalar.INSTANCE
                else -> null
            }
        }
    }
}

val graphqlUUIDType: GraphQLScalarType = GraphQLScalarType.newScalar()
    .name("UUID")
    .description("A type representing a formatted java.util.UUID")
    .coercing(UUIDCoercing)
    .build()

object UUIDCoercing : Coercing<UUID, String> {
    override fun parseValue(input: Any): UUID = UUID.fromString(serialize(input))

    override fun parseLiteral(input: Any): UUID {
        val uuidString = (input as? StringValue)?.value
        return UUID.fromString(uuidString)
    }

    override fun serialize(dataFetcherResult: Any): String = dataFetcherResult.toString()
}

@Suppress("unused")
@Component
class LicensePlateQuery(@GraphQLIgnore private val licensePlateGenerator: LicensePlateGenerator) : Query {

    // TODO petves: Skriv om med regnr
    suspend fun generateLicensePlate(): LicensePlate = licensePlateGenerator.generate()
}

@Suppress("unused")
@Component
class CarQuery(@GraphQLIgnore private val carRepository: CarRepository) : Query {
    suspend fun cars(): List<Car> = carRepository.all()
    suspend fun carByBrand(brand: String): List<Car> = carRepository.findCar(brand)

    // TODO petves: Skriv om med regnr
    suspend fun carById(id: ID): Car? = carRepository.car(id)

    fun uuidThing(): UUIDThing = UUIDThing(UUID.randomUUID(), "Foo", LocalDate.now(), OffsetDateTime.now())
}

data class CarInput(
    val brand: String,
    val model: String
)

@Suppress("unused")
@Component
class CarMutation(
    @GraphQLIgnore private val carRepository: CarRepository,
    @GraphQLIgnore private val licensePlateGenerator: LicensePlateGenerator
) : Mutation {

    suspend fun addCar(carInput: CarInput): Car {
        val car = Car(
            id = ID(licensePlateGenerator.generate().toString()),
            brand = carInput.brand,
            model = carInput.model
        )
        carRepository.add(car)

        return car
    }
}

// TODO petves: value class?
@JvmInline
value class LicensePlate(
    // TODO petves: Kontroller på indatat
    // TODO petves: Flytta generate() hit?
    val value: String
)

interface LicensePlateGenerator {

    // TODO petves: Skapa typ LicensePlate med lite regler.
    //  Det kanske är LicensePlate som ska ha generate() och inte en service?
    /**
     * @return a generated license plate.
     */
    fun generate(): LicensePlate
}

/**
 * Rules for swedish license plates (the new format): https://opus.se/nyheter/nyheter/2019-01-16-nytt-format-for-registreringsnummer
 */
@Service
class SwedishLicensePlateGenerator : LicensePlateGenerator {

    private val firstCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZÅÄÖ"
    private val lastCharacters = "ABCDEFGHJKLMNPRSTUWXYZ"
    private val digits = "0123456789"

    override fun generate(): LicensePlate {
        return LicensePlate("${firstPart()} ${secondPart()}")
    }

    private fun firstPart(): String {
        return randomize(firstCharacters, 3)
    }

    private fun secondPart(): String {
        return randomize(digits, 2) + randomize(lastCharacters, 1)
    }

    private fun randomize(sourceString: String, length: Int): String =
        Random().ints(length.toLong(), 0, sourceString.length)
            .asSequence()
            .map(sourceString::get)
            .joinToString("")
}

@Repository
class CarRepository(licensePlateGenerator: LicensePlateGenerator) {
    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    private val cars = mutableListOf(
        Car(ID(licensePlateGenerator.generate().toString()), "Porsche", "911"),
        Car(ID(licensePlateGenerator.generate().toString()), "Volvo", "V60"),
        Car(ID(licensePlateGenerator.generate().toString()), "Volvo", "142")
    )

    @EventListener
    fun event(applicationStartedEvent: ApplicationStartedEvent) {
        logger.info("Randomized cars:\n${cars.joinToString(separator = "\n")}")
    }

    fun car(id: ID): Car? = cars.firstOrNull { it.id == id }
    fun findCar(brand: String): List<Car> = cars.filter { it.brand == brand }
    fun all(): List<Car> = cars.toList()
    fun add(car: Car) {
        require(cars.firstOrNull { it.brand == car.brand && it.model == it.model } == null) { "Car with brand \"${car.brand}\" and model \"${car.model}\" already exist" }
        cars.add(car)
    }
}
