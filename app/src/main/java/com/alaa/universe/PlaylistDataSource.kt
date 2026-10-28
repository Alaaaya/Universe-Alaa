package com.alaa.universe

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException

/** Applies playlist transport policy to EVERY manifest, segment, key and subtitle request. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class PlaylistDataSource(private val delegate:DataSource,private val allowContent:()->Boolean = { true },private val allowHttp:()->Boolean):DataSource by delegate {
 override fun open(dataSpec:DataSpec):Long {
  try { check(allowContent()); ProviderRepository.validatedUrl(dataSpec.uri.toString(),allowHttp()) }
  catch (_:Exception){throw IOException("Blocked stream request by playlist transport policy.")}
  return delegate.open(dataSpec)
 }
}
