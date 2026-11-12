/** Copyright by Barry G. Becker, 2000-2018. Licensed under MIT License: http://www.opensource.org/licenses/MIT  */
package com.barrybecker4.imagebreeder

import scala.compiletime.uninitialized

import com.barrybecker4.java2d.imageproc.MetaImageOp
import com.barrybecker4.optimization.parameter.types.Parameter
import java.awt.image.BufferedImage
import java.util.concurrent.Callable
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ArrayBuffer
import scala.collection.parallel.CollectionConverters._

/**
  * Create a set of images from a single MetaImageOp.
  * Create permutations from the input image based on the metaOp passed in.
  * @param imageToBreed  some image to breed
  * @param metaOp metaOp to breed based on.
  * @param variance amount of variation to have in bred images.
  */
class ImageBreeder(var imageToBreed: BufferedImage, var metaOp: MetaImageOp, var variance: Float) {
  private var imgToParamsMap: Map[BufferedImage, Seq[Parameter]] = uninitialized

  /** @param numChildImages number of child images
    * @return list of bred images
    */
  def breedImages(numChildImages: Int): Seq[BufferedImage] = {
    val images = ArrayBuffer[BufferedImage]()
    val params = TrieMap.empty[BufferedImage, Seq[Parameter]]
    val filterTasks = (0 until numChildImages).map(_ => new Worker(metaOp, params))

    filterTasks.par.foreach(callable => images.append(callable.call()))
    imgToParamsMap = params.toMap
    images.toSeq
  }

  def getImgToParamsMap: Map[BufferedImage, Seq[Parameter]] = imgToParamsMap

  /**  Runs one of the chunks. */
  private class Worker(
      val metaOp: MetaImageOp,
      params: TrieMap[BufferedImage, Seq[Parameter]]
  ) extends Callable[BufferedImage] {
    // need to make a copy or other parallel thread may step on our internal data.
    private val myMetaOp = metaOp.copy

    override def call: BufferedImage = {
      val randOp = myMetaOp.getRandomInstance(variance)
      val img = randOp.filter(imageToBreed, null)
      params.update(img, myMetaOp.getLastUsedParameters)
      img
    }
  }
}
