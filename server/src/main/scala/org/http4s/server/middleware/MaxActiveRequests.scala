/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.server.middleware

import cats.syntax.all._
import cats.data._
import cats.effect._

import org.http4s.Status
import org.http4s.{Request, Response}
import org.http4s.ContextRequest

object MaxActiveRequests {

  def httpApp[F[_]: Sync](
      maxActive: Long,
      defaultResp: Response[F] = Response[F](status = Status.ServiceUnavailable)
  ): F[Kleisli[F, Request[F], Response[F]] => Kleisli[F, Request[F], Response[F]]] =
    inHttpApp[F, F](maxActive, defaultResp)

  def inHttpApp[G[_], F[_]](
      maxActive: Long,
      defaultResp: Response[F] = Response[F](status = Status.ServiceUnavailable)
  )(implicit F: Sync[F], G: Sync[G]): G[Kleisli[F, Request[F], Response[F]] => Kleisli[F, Request[F], Response[F]]] =
    ConcurrentRequests.app_[G, F](
      Function.const(F.unit),
      Function.const(F.unit)
    ).map(middleware =>
      (httpApp =>
        middleware(Kleisli{
          case ContextRequest(concurrent, _) if concurrent > maxActive =>
            defaultResp.pure[F]
          case ContextRequest(_, req) =>
            httpApp(req)
        })
      )
    )

  def httpRoutes[F[_]: Sync](
      maxActive: Long,
      defaultResp: Response[F] = Response[F](status = Status.ServiceUnavailable)
  ): F[Kleisli[OptionT[F, *], Request[F], Response[F]] => Kleisli[
    OptionT[F, *],
    Request[F],
    Response[F]]] =
    inHttpRoutes[F, F](maxActive, defaultResp)

  def inHttpRoutes[G[_], F[_]](
      maxActive: Long,
      defaultResp: Response[F] = Response[F](status = Status.ServiceUnavailable)
  )(implicit F: Sync[F], G: Sync[G]): G[Kleisli[OptionT[F, *], Request[F], Response[F]] => Kleisli[
    OptionT[F, *],
    Request[F],
    Response[F]]] =
    ConcurrentRequests.route_[G, F](
      Function.const(F.unit),
      Function.const(F.unit)
    ).map(middleware =>
      (httpRoutes =>
        middleware(Kleisli{
          case ContextRequest(concurrent, _) if concurrent > maxActive =>
            defaultResp.pure[OptionT[F, *]]
          case ContextRequest(_, req) =>
            httpRoutes(req)
        })
      )
    )
}
