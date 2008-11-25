/* 
 * Copyright (C) 2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.snake

import _root_.android.content.{Context, Resources}
import _root_.android.graphics.{Bitmap, Canvas, Paint}
import _root_.android.graphics.drawable.Drawable
import _root_.android.util.AttributeSet
import _root_.android.view.View

import java.util.Map

/**
 * TileView: a View-variant designed for handling arrays of "icons" or other
 * drawables.
 */
class TileView(context: Context, attrs: AttributeSet, inflateParams: Map,
               defStyle: Int) extends View(context, attrs, inflateParams, defStyle) {

  /**
   * Parameters controlling the size of the tiles and their range within view.
   * Width/Height are in pixels, and Drawables will be scaled to fit to these
   * dimensions. X/Y Tile Counts are the number of tiles that will be drawn.
   */
  protected var mTileSize: Int = _

  protected var mXTileCount: Int = _
  protected var mYTileCount: Int = _

  private var mXOffset: Int = _
  private var mYOffset: Int = _

  /**
   * A hash that maps integer handles specified by the subclasser to the
   * drawable that will be used for that reference
   */
  private var mTileArray: Array[Bitmap] = _

  /**
   * A two-dimensional array of integers in which the number represents the
   * index of the tile that should be drawn at that locations
   */
  private var mTileGrid: Array[Array[Int]] = _

  private val mPaint = new Paint()

  mTileSize = {
    val a = context.obtainStyledAttributes(attrs, R.styleable.TileView)
    a.getInt(R.styleable.TileView_tileSize, 12)
  }

  def this(context: Context, attrs: AttributeSet, inflateParams: Map) =
    this(context, attrs, inflateParams, 0)
    
  /**
   * Rests the internal array of Bitmaps used for drawing tiles, and
   * sets the maximum index of tiles to be inserted
   * 
   * @param tilecount
   */
  def resetTiles(tilecount: Int) {
    mTileArray = new Array[Bitmap](tilecount)
  }

  override protected def onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    mXTileCount = java.lang.Math.floor(w / mTileSize).toInt
    mYTileCount = java.lang.Math.floor(h / mTileSize).toInt

    mXOffset = (w - (mTileSize * mXTileCount)) / 2
    mYOffset = (h - (mTileSize * mYTileCount)) / 2

    mTileGrid = new Array[Array[Int]](mXTileCount, mYTileCount)
    clearTiles()
  }

  /**
   * Function to set the specified Drawable as the tile for a particular
   * integer key.
   * 
   * @param key
   * @param tile
   */
  def loadTile(key: Int, tile: Drawable) {
    val bitmap = Bitmap.createBitmap(mTileSize, mTileSize, true)
    val canvas = new Canvas(bitmap)
    tile.setBounds(0, 0, mTileSize, mTileSize)
    tile draw canvas

    mTileArray(key) = bitmap
  }

  /**
   * Resets all tiles to 0 (empty)
   * 
   */
  def clearTiles() {
    for (x <- 0 until mXTileCount; y <- 0 until mYTileCount)
      setTile(0, x, y)
  }

  /**
   * Used to indicate that a particular tile (set with loadTile and referenced
   * by an integer) should be drawn at the given x/y coordinates during the
   * next invalidate/draw cycle.
   * 
   * @param tileindex
   * @param x
   * @param y
   */
  def setTile(tileindex: Int, x: Int, y: Int) {
    mTileGrid(x)(y) = tileindex
  }

  override def onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    for (x <- 0 until mXTileCount; y <- 0 until mYTileCount if mTileGrid(x)(y) > 0)
      canvas.drawBitmap(mTileArray(mTileGrid(x)(y)), 
                        mXOffset + x * mTileSize,
                    	mYOffset + y * mTileSize,
                    	mPaint)
  }

}
