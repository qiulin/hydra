package hydra.ingest.bootstrap

import hydra.ingest.test.{TestIngestor, TestTransport}
import org.scalatest.{FunSpecLike, Matchers}

/**
  * Created by alexsilva on 3/26/17.
  */
class ClasspathHydraComponentLoaderSpec extends Matchers with FunSpecLike {

  describe("When scanning the classpath for ingestors") {
    it("loads ingestors from a package") {
      ClasspathHydraComponentLoader.ingestors should contain (classOf[TestIngestor])
    }

    it("loads transports from a package") {
      ClasspathHydraComponentLoader.transports should contain (classOf[TestTransport])
    }
  }
}
