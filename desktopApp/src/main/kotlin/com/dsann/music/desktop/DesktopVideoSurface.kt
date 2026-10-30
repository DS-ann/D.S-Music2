package com.dsann.music.desktop

import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.scene.paint.Color as FxColor
import java.awt.Dimension

/** JavaFX renderer hosted by Compose Desktop through SwingPanel. */
internal class DesktopVideoSurface : JFXPanel() {
    @Volatile private var attachedPlayer: MediaPlayer? = null

    init {
        preferredSize = Dimension(1280, 720)
        minimumSize = Dimension(320, 180)
        Platform.setImplicitExit(false)
        Platform.runLater {
            scene = Scene(StackPane(), 1280.0, 720.0, FxColor.BLACK)
        }
    }

    fun attach(player: MediaPlayer?) {
        if (attachedPlayer === player) return
        attachedPlayer = player
        Platform.runLater {
            val root = StackPane()
            if (player != null) {
                root.children.add(
                    MediaView(player).apply {
                        isPreserveRatio = true
                        fitWidth = 1280.0
                        fitHeight = 720.0
                    }
                )
            }
            scene = Scene(root, 1280.0, 720.0, FxColor.BLACK)
        }
    }
}
