package com.dsann.music.desktop

import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.MenuButton
import javafx.scene.control.MenuItem
import javafx.scene.layout.HBox
import javafx.scene.layout.StackPane
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.scene.paint.Color as FxColor
import java.awt.Dimension

/**
 * JavaFX video renderer hosted by Compose Desktop through SwingPanel.
 * Quality and fullscreen actions live in the video surface itself.
 */
internal class DesktopVideoSurface : JFXPanel() {
    @Volatile private var attachedPlayer: MediaPlayer? = null
    @Volatile private var qualityOptions: List<DesktopVideoOption> = emptyList()
    @Volatile private var selectedQuality: String? = null
    @Volatile private var onQualitySelected: ((DesktopVideoOption) -> Unit)? = null
    @Volatile private var onFullscreenToggle: (() -> Unit)? = null

    private val root = StackPane()
    private val mediaView = MediaView()
    private val qualityMenu = MenuButton("⚙")
    private val fullscreenButton = Button("⛶")

    init {
        preferredSize = Dimension(1280, 720)
        minimumSize = Dimension(320, 180)
        Platform.setImplicitExit(false)

        qualityMenu.style = controlStyle()
        qualityMenu.tooltip = javafx.scene.control.Tooltip("Video quality")
        fullscreenButton.style = controlStyle()
        fullscreenButton.tooltip = javafx.scene.control.Tooltip("Full screen")

        qualityMenu.setOnShowing { rebuildQualityMenu() }
        fullscreenButton.setOnAction { onFullscreenToggle?.invoke() }

        val controls = HBox(8.0, qualityMenu, fullscreenButton).apply {
            style = "-fx-padding: 12px; -fx-background-color: transparent;"
            alignment = Pos.CENTER_RIGHT
        }
        mediaView.isPreserveRatio = true
        mediaView.fitWidth = 1280.0
        mediaView.fitHeight = 720.0
        root.children.setAll(mediaView, controls)
        StackPane.setAlignment(mediaView, Pos.CENTER)
        StackPane.setAlignment(controls, Pos.TOP_RIGHT)

        Platform.runLater {
            scene = Scene(root, 1280.0, 720.0, FxColor.BLACK)
        }
    }

    fun attach(player: MediaPlayer?) {
        if (attachedPlayer === player) return
        attachedPlayer = player
        Platform.runLater {
            mediaView.mediaPlayer = player
        }
    }

    fun setVideoControls(
        options: List<DesktopVideoOption>,
        selectedLabel: String?,
        onQuality: (DesktopVideoOption) -> Unit,
        onFullscreen: () -> Unit
    ) {
        qualityOptions = options
        selectedQuality = selectedLabel
        onQualitySelected = onQuality
        onFullscreenToggle = onFullscreen
        Platform.runLater {
            qualityMenu.isDisable = options.isEmpty()
            rebuildQualityMenu()
        }
    }

    private fun rebuildQualityMenu() {
        qualityMenu.items.clear()
        qualityOptions.forEach { option ->
            val label = if (option.label == selectedQuality) "✓  ${option.label}" else option.label
            qualityMenu.items.add(
                MenuItem(label).apply {
                    setOnAction { onQualitySelected?.invoke(option) }
                }
            )
        }
    }

    private fun controlStyle(): String =
        "-fx-background-color: rgba(0, 0, 0, 0.68); -fx-text-fill: white; -fx-font-size: 16px;"

}
