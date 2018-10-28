/** Copyright by Barry G. Becker, 2000-2018. Licensed under MIT License: http://www.opensource.org/licenses/MIT  */
package com.barrybecker4.imagebreeder

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
import javax.swing.JButton
import javax.swing.JPanel
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GridLayout
import java.awt.Label
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import java.awt.image.BufferedImage


/**
  * Allows you to mix filters together using a genetic algorithm
  * in order to produce very interesting results.
  */
object ImageBreederApplet extends App {
  private val NUM_CHILD_IMAGES = 20
  private val DEFAULT_VARIANCE = 0.2f
  private val IMAGE_DIR = "com/barrybecker4/imagebreeder/images/" // NON-NLS

  private val DEFAULT_IMAGE = "brian_in_surf1_big_hair_smaller.jpg"

  var imageFile = IMAGE_DIR + DEFAULT_IMAGE
  val opts = new CommandLineOptions(args)
  if (opts.contains("imageFile")) {
    imageFile = opts.getValueForOption("imageFile")
    System.out.println("imageFile = " + imageFile)
  }
  val breeder = new ImageBreederApplet(imageFile)
  GUIUtil.showApplet(breeder)
}

class ImageBreederApplet()
  extends ApplicationApplet with ItemListener with ActionListener with ImageSelectionListener with SliderChangeListener {

  private var operations = new ProcessingOperators
  createImageFrame(ImageBreederApplet.IMAGE_DIR + ImageBreederApplet.DEFAULT_IMAGE)
  private var variance = ImageBreederApplet.DEFAULT_VARIANCE
  private var currentImage: BufferedImage = _
  private var imageListPanel: ImageListPanel = _
  private var loadButton:JButton = _
  private var newGenerationButton: JButton = _
  private var lastGenerationButton: JButton = _
  private val statusLabel = new Label("")
  private var mainPanel: JPanel = _
  private var paramPanel: ParameterPanel = _
  private var filterList: java.awt.List = _
  private var imgToParamsMap: Map[BufferedImage, Seq[Parameter]] = _
  private var lastImgToParamsMap: Map[BufferedImage, Seq[Parameter]] = _
  private var lastImages: Seq[BufferedImage] = _
  private var lastSelectedIndices: Seq[Int] = _
  private var lastSelectedFilterIndex: Int = 0
  private var currentSelectedFilterIndex: Int = 0
  private var generationCountForFilter: Int = 0

  /** @param imageFile image to breed  */
  def this(imageFile: String) {
    this()
    operations = new ProcessingOperators
    createImageFrame(imageFile)
  }

  /**
    * The generated images are shown in a separate window.
    * @param imageFile file containing image to breed
    */
  private def createImageFrame(imageFile: String): Unit = {
    println("The imageFile == " + imageFile)
    currentImage = GUIUtil.getBufferedImage(imageFile)
    println("currentImage=" + currentImage + "\n from " + imageFile)
    // also create image list panel
    imageListPanel = new ImageListPanel
    imageListPanel.setMaxNumSelections(2)
    imageListPanel.setPreferredSize(new Dimension(700, 300))
    imageListPanel.addImageSelectionListener(this)
    val mImageListFrame = new Frame(imageFile)
    mImageListFrame.setLayout(new BorderLayout)
    mImageListFrame.add(imageListPanel, BorderLayout.CENTER)
    Utilities.sizeContainerToComponent(mImageListFrame, imageListPanel)
    mImageListFrame.setVisible(true)
  }

  override def createMainPanel: JPanel = {
    filterList = operations.getSortedKeys
    // When an item is selected, do the corresponding transformation.
    filterList.addItemListener(this)
    // arbitrarily select the first one
    filterList.select(0)
    lastSelectedFilterIndex = filterList.getSelectedIndex
    currentSelectedFilterIndex = lastSelectedFilterIndex
    mainPanel = new JPanel(new BorderLayout)
    // add placeholder param panel
    paramPanel = new ParameterPanel(null)
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
    val varianceSlider = new LabeledSlider("Variance", ImageBreederApplet.DEFAULT_VARIANCE, 0.0, 0.5)
    varianceSlider.addChangeListener(this)
    newGenerationButton = new JButton("New Generation")
    newGenerationButton.setToolTipText(
      "Create a new generation of images using the current selected image as a parent.")
    newGenerationButton.addActionListener(this)
    lastGenerationButton = new JButton("Last Generation")
    newGenerationButton.setToolTipText(
      "Go back to the last generation of images that were based on the current selection's parent.")
    lastGenerationButton.addActionListener(this)
    // initially there is nothing to go back to.
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

  private def fill = {
    val fill = new JPanel
    fill.setPreferredSize(new Dimension(1000, 10))
    fill
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
    // now show ui for modifying the parameters for this op
    mainPanel.remove(paramPanel)
    paramPanel = new ParameterPanel(metaOp.getBaseParameters)
    // don't called whenever a parameter is tweeked
    // paramPanel.addParameterChangeListener(this);
    mainPanel.add(paramPanel, BorderLayout.CENTER)
    mainPanel.doLayout() //doLayout(); //pack();

    mainPanel.validate()
    mainPanel.repaint()
  }

  private def createImagesForSelectedFilter(): Unit = {
    // we could use param.getName() to get the filter, but its just the currently selected one.
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
    lastImgToParamsMap = null
    lastImages = null
    lastGenerationButton.setEnabled(false)
  }

  private def applyImageOperator(metaOp: MetaImageOp): Unit = {
    val key = filterList.getSelectedItem
    statusLabel.setText("Performing " + key + "...")
    // create a bunch of child permutations and add them to the imageListPanel
    lastImgToParamsMap = imgToParamsMap
    lastImages = imageListPanel.getImageList
    lastSelectedIndices = imageListPanel.getSelectedImageIndices
    generationCountForFilter += 1
    enableUI(false)
    val time = System.currentTimeMillis
    val fb = new ImageBreeder(currentImage, metaOp, variance)
    val images = fb.breedImages(ImageBreederApplet.NUM_CHILD_IMAGES)
    assert(images != null)
    assert(images.nonEmpty)
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
    if (lastImages != null) lastGenerationButton.setEnabled(enable)
  }

  /** Called when the load button or go button is pressed.
    * @param ae assertion error
    */
  override def actionPerformed(ae: ActionEvent): Unit = {
    val button = ae.getSource.asInstanceOf[JButton]
    if (button eq newGenerationButton) createImagesForSelectedFilter()
    else if (button eq lastGenerationButton) restoreLastGeneration()
    else if (button eq loadButton) {
      val f: Frame = null
      val fd = new FileDialog(f)
      fd.setVisible(true)
      if (fd.getFile == null) return
      val path = fd.getDirectory + fd.getFile
      currentImage = Utilities.getBufferedImage(path)
      createImagesForSelectedFilter()
    }
    else throw new IllegalStateException("unexpected source: " + button.getText)
  }

  override def sliderChanged(slider: LabeledSlider): Unit = {
    variance = slider.getValue.toFloat
  }

  /** Make the parameters setting match the last selected image.
    * @param img the image to use as a basis for breeding.
    */
  override def imageSelected(img: BufferedImage): Unit = {
    val params = imgToParamsMap(img)
    assert(params != null)
    //System.out.println("image selected params = " + params);
    paramPanel.updateParameters(params)
  }

  override def getName = "Image Breeder"
}