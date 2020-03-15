package com.barrybecker4.imagebreeder

import com.barrybecker4.ui.util.GUIUtil
import com.barrybecker4.imagebreeder.ImageBreederConsts.{DEFAULT_IMAGE, IMAGE_DIR}
import com.barrybecker4.java2d.imageproc.ProcessingOperators
import org.scalatest.funsuite.AnyFunSuite

class ImageBreederSuite extends AnyFunSuite {

  test("construction") {
    val imageFile =  IMAGE_DIR + DEFAULT_IMAGE
    val currentImage = GUIUtil.getBufferedImage(imageFile)
    val operations = new ProcessingOperators
    val metaOp = operations.getOperation(operations.getSortedKeys.getItem(0))
    val fb = new ImageBreeder(currentImage, metaOp, 0.1f)
    assertResult(356) {fb.imageToBreed.getHeight}
  }
}
