package com.diettracker.http

import EndpointSupport._
import com.diettracker.domain._
import com.diettracker.services.FoodService
import com.diettracker.services.FoodService.CreateFoodRequest
import sttp.tapir._
import sttp.tapir.json.zio._
import sttp.tapir.server.ServerEndpoint
import zio._

object FoodsEndpoints {
  private val searchQuery: EndpointInput[Option[String]] = query[Option[String]]("q")
  private val forceQuery: EndpointInput[Boolean]         =
    query[Option[Boolean]]("force").map(_.getOrElse(false))(force => Some(force))
  private val foodsTag                                   = "Foods"

  val createFoodEndpoint: PublicEndpoint[(UserId, CreateFoodPayload), ApiFailure, FoodResponse, Any] =
    endpoint.post
      .in("foods")
      .in(userIdHeader)
      .in(jsonBody[CreateFoodPayload])
      .tag(foodsTag)
      .name("Create Food")
      .summary("Create a custom food item")
      .description("Creates a food item owned by the authenticated user that can later be used in logs.")
      .out(jsonBody[FoodResponse])
      .errorOut(errorOutput)

  val searchFoodsEndpoint: PublicEndpoint[(UserId, Option[String]), ApiFailure, FoodSearchResponse, Any] =
    endpoint.get
      .in("foods")
      .in(userIdHeader)
      .in(searchQuery)
      .tag(foodsTag)
      .name("List/Search Foods")
      .summary("List foods for the authenticated user")
      .description("Returns foods owned by the user, optionally filtered by a case-insensitive text query.")
      .out(jsonBody[FoodSearchResponse])
      .errorOut(errorOutput)

  val updateFoodEndpoint: PublicEndpoint[(FoodId, UserId, CreateFoodPayload), ApiFailure, FoodResponse, Any] =
    endpoint.patch
      .in("foods" / path[FoodId]("id"))
      .in(userIdHeader)
      .in(jsonBody[CreateFoodPayload])
      .tag(foodsTag)
      .name("Update Food")
      .summary("Update an existing food item")
      .description("Updates a custom food and returns the new version, enforcing validation rules.")
      .out(jsonBody[FoodResponse])
      .errorOut(errorOutput)

  val deleteFoodEndpoint: PublicEndpoint[(FoodId, UserId, Boolean), ApiFailure, Unit, Any] =
    endpoint.delete
      .in("foods" / path[FoodId]("id"))
      .in(userIdHeader)
      .in(forceQuery)
      .tag(foodsTag)
      .name("Delete Food")
      .summary("Delete a custom food item")
      .description("Deletes a food if no logs reference it (or when acknowledged via force flag).")
      .out(emptyOutput)
      .errorOut(errorOutput)

  def routes(foodService: FoodService): List[ServerEndpoint[Any, Task]] =
    List(
      createRoute(foodService),
      searchRoute(foodService),
      updateRoute(foodService),
      deleteRoute(foodService)
    )

  private def createRoute(foodService: FoodService): ServerEndpoint[Any, Task] =
    createFoodEndpoint.serverLogic { case (userId, payload) =>
      foodService
        .createFood(userId, toDomainPayload(payload))
        .map(FoodResponse.fromDomain)
        .map(Right(_))
        .catchAll(error => ZIO.succeed(Left(toFailure(error))))
    }

  private def searchRoute(foodService: FoodService): ServerEndpoint[Any, Task] =
    searchFoodsEndpoint.serverLogic { case (userId, query) =>
      foodService
        .searchFoods(userId, query)
        .map(items => FoodSearchResponse(items.map(FoodResponse.fromDomain).toList))
        .map(Right(_))
        .catchAll(error => ZIO.succeed(Left(toFailure(error))))
    }

  private def updateRoute(foodService: FoodService): ServerEndpoint[Any, Task] =
    updateFoodEndpoint.serverLogic { case (foodId, userId, payload) =>
      foodService
        .updateFood(foodId, userId, toDomainPayload(payload))
        .map(FoodResponse.fromDomain)
        .map(Right(_))
        .catchAll(error => ZIO.succeed(Left(toFailure(error))))
    }

  private def deleteRoute(foodService: FoodService): ServerEndpoint[Any, Task] =
    deleteFoodEndpoint.serverLogic { case (foodId, userId, force) =>
      foodService
        .deleteFood(foodId, userId, force)
        .as(Right(()))
        .catchAll(error => ZIO.succeed(Left(toFailure(error))))
    }

  private def toDomainPayload(payload: CreateFoodPayload): CreateFoodRequest = {
    val normalizedName  = FoodName(payload.name.value.trim)
    val normalizedBrand =
      payload.brand.flatMap(brand => Option(brand.value.trim).filter(_.nonEmpty).map(FoodBrand(_)))
    val normalizedUnit  = ServingUnit(payload.defaultServingUnit.value.trim)
    val normalizedNotes =
      payload.notes.flatMap(note => Option(note.value.trim).filter(_.nonEmpty).map(FoodNotes(_)))

    CreateFoodRequest(
      name = normalizedName,
      brand = normalizedBrand,
      defaultServingValue = payload.defaultServingValue,
      defaultServingUnit = normalizedUnit,
      caloriesPerServing = payload.caloriesPerServing,
      macrosPerServing = payload.macrosPerServing,
      macrosPer100g = payload.macrosPer100g,
      notes = normalizedNotes
    )
  }
}
