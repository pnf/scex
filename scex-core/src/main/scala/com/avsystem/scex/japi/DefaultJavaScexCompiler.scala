package com.avsystem.scex
package japi

import com.avsystem.scex.compiler.{CachingScexCompiler, ScexCompilerConfig}
import java.{util => ju, lang => jl}
import com.avsystem.scex.compiler.presentation.{CachingScexPresentationCompiler, ScexPresentationCompiler}

/**
 * Created: 17-09-2013
 * Author: ghik
 */
class DefaultJavaScexCompiler(val config: ScexCompilerConfig)
  extends CachingScexCompiler with ScexPresentationCompiler with CachingScexPresentationCompiler with JavaScexCompiler