/** Copyright by Barry G. Becker, 2000-2018. Licensed under MIT License: http://www.opensource.org/licenses/MIT  */
package com.barrybecker4.imagebreeder

import scala.compiletime.uninitialized

import com.barrybecker4.common.app.CommandLineOptions
import com.barrybecker4.java2d.Utilities
import com.barrybecker4.java2d.imageproc.MetaImageOp
import com.barrybecker4.java2d.imageproc.ParameterPanel
import com.barrybecker4.java2d.imageproc.ProcessingOperators
import com.barrybecker4.optimization.parameter.types.Parameter
import com.barrybecker4.ui.application.ApplicationApplet
import com.barrybecker4.ui.components.ImageListPanel
import com.barrybecker4.ui.components.ImageSelectionListener
import com.barrybecker4.ui.sliders.LabeledSlider
import com.barrybecker4.ui.sliders.SliderChangeListener
import com.barrybecker4.ui.util.GUIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GridLayout
import java.awt.Label
import java.awt.List
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import java.awt.image.BufferedImage
import javax.swing.JButton
import javax.swing.JPanel
import ImageBreederConsts.{DEFAULT_IMAGE, DEFAULT_VARIANCE, IMAGE_DIR, NUM_CHILD_IMAGES}

object ImageBreederConsts {
  val NUM_CHILD_IMAGES = 20
  val DEFAULT_VARIANCE = 0.2f
  val IMAGE_DIR = "com/barrybecker4/imagebreeder/images/" // NON-NLS
  val DEFAULT_IMAGE = "brian_in_surf1_big_hair_smaller.jpg"
}

/**
  * Allows you to mix filters together using a genetic algorithm
  * in order to produce very interesting results.
  */
object ImageBreederApp {
  def main(args: Array[String]): Unit = {
    var imageFile = IMAGE_DIR + DEFAULT_IMAGE
    val opts = new CommandLineOptions(args)
    if (opts.contains("imageFile")) {
      imageFile = opts.getValueForOption("imageFile", IMAGE_DIR + DEFAULT_IMAGE)
      println("imageFile = " + imageFile)
    }
    GUIUtil.showApplet(new ImageBreederApp(args, imageFile))
  }
}

class ImageBreederApp(args: Array[String], imageFile: String)
  extends ApplicationApplet(args)
    with ItemListener with ActionListener with ImageSelectionListener with SliderChangeListener {

  private val operations = new ProcessingOperators
  private var variance = DEFAULT_VARIANCE
  private var currentImage: BufferedImage = uninitialized
  private var imageListPanel: ImageListPanel = uninitialized
  private var loadButton: JButton = uninitialized
  private var newGenerationButton: JButton = uninitialized
  private var lastGenerationButton: JButton = uninitialized
  private val statusLabel = new Label("")
  private var mainPanel: JPanel = uninitialized
  private var paramPanel: ParameterPanel = uninitialized
  private var filterList: List = uninitialized
  private var imgToParamsMap: Map[BufferedImage, Seq[Parameter]] = Map.empty
  private var lastImgToParamsMap: Map[BufferedImage, Seq[Parameter]] = Map.empty
  private var lastImages: Seq[BufferedImage] = Seq.empty
  private var lastSelectedIndices: Seq[Int] = Seq.empty
  private var lastSelectedFilterIndex: Int = 0
  private var currentSelectedFilterIndex: Int = 0
  private var generationCountForFilter: Int = 0
  private var hasLastGeneration: Boolean = false

  createImageFrame(imageFile)

  /**
    * The generated images are shown in a separate window.
    * @param imageFilePath file containing image to breed
    */
  private def createImageFrame(imageFilePath: String): Unit = {
    println("The imageFile == " + imageFilePath)

    currentImage = GUIUtil.getBufferedImage(imageFilePath, this)

    println("currentImage=" + currentImage + "\n from " + imageFilePath)
    imageListPanel = new ImageListPanel
    imageListPanel.setMaxNumSelections(2)
    imageListPanel.setPreferredSize(new Dimension(700, 300))
    imageListPanel.addImageSelectionListener(this)
    val mImageListFrame = new Frame(imageFilePath)
    mImageListFrame.setLayout(new BorderLayout)
    mImageListFrame.add(imageListPanel, BorderLayout.CENTER)
    Utilities.sizeContainerToComponent(mImageListFrame, imageListPanel)
    mImageListFrame.setVisible(true)
  }

  override def createMainPanel: JPanel = {
    filterList = new List()
    for (item <- operations.getSortedKeys)
      filterList.add(item)
    filterList.addItemListener(this)
    filterList.select(0)
    lastSelectedFilterIndex = filterList.getSelectedIndex
    currentSelectedFilterIndex = lastSelectedFilterIndex
    mainPanel = new JPanel(new BorderLayout)
    paramPanel = new ParameterPanel(None)
    mainPanel.add(paramPanel, BorderLayout.CENTER)
    mainPanel.add(filterList, BorderLayout.WEST)
    mainPanel.add(createBottomControls, BorderLayout.SOUTH)
    createImagesForSelectedFilter()
    mainPanel
  }

  override def getSize = new Dimension(400, 500)

  private def createBottomControls = {
    loadButton = new JButton("Load...")
    loadButton.addActionListener(this)
    val varianceSlider = new LabeledSlider("Variance", DEFAULT_VARIANCE, 0.0, 0.5)
    varianceSlider.addChangeListener(this)
    newGenerationButton = new JButton("New Generation")
    newGenerationButton.setToolTipText(
      "Create a new generation of images using the current selected image as a parent.")
    newGenerationButton.addActionListener(this)
    lastGenerationButton = new JButton("Last Generation")
    lastGenerationButton.setToolTipText(
      "Go back to the last generation of images that were based on the current selection's parent.")
    lastGenerationButton.addActionListener(this)
    lastGenerationButton.setEnabled(false)
    val controls = new JPanel(new BorderLayout)
    val topControls = new JPanel(new GridLayout(2, 1))
    val top = new JPanel
    val middle = new JPanel
    top.add(loadButton)
    top.add(varianceSlider)
    middle.add(newGenerationButton)
    middle.add(lastGenerationButton)
    controls.setPreferredSize(new Dimension(300, 120))
    topControls.add(top)
    topControls.add(middle)
    controls.add(topControls, BorderLayout.WEST)
    controls.add(statusLabel, BorderLayout.SOUTH)
    controls
  }

  /** Called when an item in the list of transformations is called. */
  override def itemStateChanged(ie: ItemEvent): Unit = {
    if (ie.getStateChange != ItemEvent.SELECTED) return
    if (filterList.getSelectedIndex != lastSelectedFilterIndex) {
      lastSelectedFilterIndex = currentSelectedFilterIndex
      currentSelectedFilterIndex = filterList.getSelectedIndex
      generationCountForFilter = 0
      updateParameterUI(true)
    }
  }

  private def updateParameterUI(recalc: Boolean): Unit = {
    val key = filterList.getSelectedItem
    val metaOp = operations.getOperation(key)
    replaceParameterUI(metaOp)
    if (recalc) applyImageOperator(metaOp)
  }

  private def replaceParameterUI(metaOp: MetaImageOp): Unit = {
    mainPanel.remove(paramPanel)
    paramPanel = new ParameterPanel(Some(metaOp.getBaseParameters))
    mainPanel.add(paramPanel, BorderLayout.CENTER)
    mainPanel.doLayout()
    mainPanel.validate()
    mainPanel.repaint()
  }

  private def createImagesForSelectedFilter(): Unit = {
    val key = filterList.getSelectedItem
    val metaOp = operations.getOperation(key)
    applyImageOperator(metaOp)
  }

  private def restoreLastGeneration(): Unit = {
    if (lastSelectedFilterIndex != filterList.getSelectedIndex && generationCountForFilter <= 1) {
      filterList.select(lastSelectedFilterIndex)
      updateParameterUI(false)
    }
    imgToParamsMap = lastImgToParamsMap
    imageListPanel.setImageList(lastImages)
    imageListPanel.setSelectedImageIndices(lastSelectedIndices)
    hasLastGeneration = false
    lastGenerationButton.setEnabled(false)
  }

  private def applyImageOperator(metaOp: MetaImageOp): Unit = {
    val key = filterList.getSelectedItem
    statusLabel.setText("Performing " + key + "...")
    lastImgToParamsMap = imgToParamsMap
    lastImages = Option(imageListPanel.getImageList).getOrElse(Seq.empty)
    lastSelectedIndices = imageListPanel.getSelectedImageIndices
    hasLastGeneration = lastImages.nonEmpty
    generationCountForFilter += 1
    enableUI(false)
    val time = System.currentTimeMillis
    val fb = new ImageBreeder(currentImage, metaOp, variance)
    val images = fb.breedImages(NUM_CHILD_IMAGES)
    assert(images != null && images.nonEmpty)
    imgToParamsMap = fb.getImgToParamsMap
    val elapsedTime = ((System.currentTimeMillis - time) / 1000).toInt
    statusLabel.setText("Performing " + key + "...done in " + elapsedTime + " seconds")
    imageListPanel.setImageList(images)
    enableUI(true)
  }

  private def enableUI(enable: Boolean): Unit = {
    filterList.setEnabled(enable)
    newGenerationButton.setEnabled(enable)
    loadButton.setEnabled(enable)
    if (hasLastGeneration) lastGenerationButton.setEnabled(enable)
  }

  /** Called when the load button or go button is pressed. */
  override def actionPerformed(ae: ActionEvent): Unit = {
    val button = ae.getSource.asInstanceOf[JButton]
    if (button eq newGenerationButton) createImagesForSelectedFilter()
    else if (button eq lastGenerationButton) restoreLastGeneration()
    else if (button eq loadButton) {
      val fd = new FileDialog(null.asInstanceOf[Frame])
      fd.setVisible(true)
      if (fd.getFile == null) return
      val path = fd.getDirectory + fd.getFile
      println("path = " + path)
      currentImage = Utilities.getBufferedImage(path)
      createImagesForSelectedFilter()
    }
    else throw new IllegalStateException("unexpected source: " + button.getText)
  }

  override def sliderChanged(slider: LabeledSlider): Unit = {
    variance = slider.getValue.toFloat
  }

  /** Make the parameters setting match the last selected image. */
  override def imageSelected(img: BufferedImage): Unit = {
    val params = imgToParamsMap(img)
    assert(params != null)
    paramPanel.updateParameters(params)
  }

  override def getName = "Image Breeder"
}
