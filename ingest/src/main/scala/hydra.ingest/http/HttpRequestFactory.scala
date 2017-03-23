package hydra.ingest.http

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import hydra.core.ingest.IngestionParams._
import hydra.core.ingest._
import hydra.core.produce.ValidationStrategy

import scala.concurrent.Future

/**
  * Created by alexsilva on 3/14/17.
  */
class HttpRequestFactory extends RequestFactory[String, HttpRequest] {

  override def createRequest(correlationId: String, request: HttpRequest)
                            (implicit mat: Materializer): Future[HydraRequest] = {
    implicit val ec = mat.executionContext

    val rs = request.headers.find(_.lowercaseName() == HYDRA_RETRY_STRATEGY)
      .map(h => RetryStrategy(h.value())).getOrElse(RetryStrategy.Fail)

    val vs = request.headers.find(_.lowercaseName() == HYDRA_VALIDATION_STRATEGY)
      .map(h => ValidationStrategy(h.value())).getOrElse(ValidationStrategy.Strict)

    Unmarshal(request.entity).to[String].map { payload =>
      val metadata: List[HydraRequestMedatata] = List(request.headers.map(header =>
        HydraRequestMedatata(header.name.toLowerCase, header.value)): _*)
      HydraRequest(correlationId, payload, metadata, retryStrategy = rs, validationStrategy = vs)
    }
  }
}