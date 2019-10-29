package servicetmpl

import com.spotify.docker.client.{DefaultDockerClient, DockerClient}
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.{DockerFactory, DockerKit}

trait SpotifyDockerKit extends DockerKit {

  private val client: DockerClient = DefaultDockerClient.fromEnv().build()

  override implicit val dockerFactory: DockerFactory = new SpotifyDockerFactory(client)
}
