/** Copyright by Barry G. Becker, 2000-2018. Licensed under MIT License: http://www.opensource.org/licenses/MIT  */
package com.barrybecker4.imagebreeder

import com.barrybecker4.common.concurrency.CallableParallelizer
import com.barrybecker4.java2d.imageproc.MetaImageOp
import com.barrybecker4.optimization.parameter.types.Parameter
import java.awt.image.BufferedImage
import java.util.concurrent.Callable
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConverters._


/**
  * Create a set of images from a single MetaImageOp.
  * Create permutations from the input image based on the metaOp passed in.
  * @param imageToBreed  some image to breed
  * @param metaOp    metaOp to breed based on.
  * @param variance amount of variation to have in bred images.
  */
class ImageBreeder(var imageToBreed: BufferedImage, var metaOp: MetaImageOp, var variance: Float) {
  private var imgToParamsMap: Map[BufferedImage, Seq[Parameter]] = _
  private val parallelizer = new CallableParallelizer[BufferedImage](Runtime.getRuntime.availableProcessors)

  /** @param numChildImages number of child images
    * @return list of bred images
    */
  def breedImages(numChildImages: Int): Seq[BufferedImage] = {
    val images = ArrayBuffer[BufferedImage]()
    imgToParamsMap = Map[BufferedImage, Seq[Parameter]]()
    val filterTasks = ArrayBuffer[Callable[BufferedImage]]()
    var i = 0
    while (i < numChildImages) {
      filterTasks.append(new Worker(metaOp))
      i += 1
    }

    parallelizer.invokeAllWithCallback(
      filterTasks.toList.asJava,
      (img: BufferedImage) => {images.append(img)}
    )
    images
  }

  def getImgToParamsMap: Map[BufferedImage, Seq[Parameter]] = imgToParamsMap

  /**  Runs one of the chunks. */
  private class Worker(val metaOp: MetaImageOp) extends Callable[BufferedImage] {
    // need to make a copy or other parallel thread may step on our internal data.
    private var myMetaOp = metaOp.copy

    override def call: BufferedImage = {
      val randOp = myMetaOp.getRandomInstance(variance)
      val img = randOp.filter(imageToBreed, null)
      // remember the parameters that were used to create this instance;
      imgToParamsMap += img -> myMetaOp.getLastUsedParameters
      img
    }
  }
}