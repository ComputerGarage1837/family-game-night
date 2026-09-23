import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.familygamenight.app.ui.TableModel
import com.familygamenight.app.ui.colorFor
import com.familygamenight.app.ui.table.GoFishTable
import com.familygamenight.app.ui.theme.FamilyGameNightTheme
import com.familygamenight.core.game.Difficulty
import com.familygamenight.core.gofish.GoFish
import com.familygamenight.core.gofish.GoFishAi
import com.familygamenight.core.gofish.GoFishConfig
import com.familygamenight.core.gofish.GoFishModule
import com.familygamenight.core.gofish.GoFishRuleIds
import com.familygamenight.core.session.Seat
import com.familygamenight.core.session.SeatKind
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.random.Random

fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "out" }).apply { mkdirs() }
    val names = listOf("Mum", "Dad", "Sam", "Merlin", "Lady Wren", "Gran", "Sir Pip", "Ellie", "Tom", "Friar Tuck")
    // players, moves already played, width px, height px, density (2.0 ≈ small phone, 2.75 ≈ modern phone)
    val shots = listOf(
        listOf(4f, 14f, 2340f, 1080f, 2.75f),
        listOf(10f, 30f, 2340f, 1080f, 2.75f),
        listOf(2f, 6f, 1920f, 1080f, 2.75f),
        listOf(6f, 0f, 1600f, 900f, 2.5f),
        listOf(8f, 20f, 1520f, 720f, 2.0f),
    )
    for (shot in shots) {
        val players = shot[0].toInt(); val moves = shot[1].toInt(); val w = shot[2].toInt(); val h = shot[3].toInt()
        val random = Random(players * 31 + moves)
        var s = GoFish.deal(GoFishConfig.from(players, mapOf(GoFishRuleIds.TWO_DECKS to true, GoFishRuleIds.MEMORY_HELPER to true)), random)
        repeat(moves) {
            if (!s.over) s = GoFish.apply(s, s.current, GoFishAi.choose(GoFish.view(s, s.current), Difficulty.HARD, random))
        }
        val seats = (0 until players).map { i ->
            Seat(i, "p$i", names[i], if (i == 0) SeatKind.LOCAL else if (i % 3 == 0) SeatKind.REMOTE else SeatKind.AI,
                connected = !(players == 6 && i == 3))
        }
        val viewer = 0
        val model = TableModel(GoFishModule, emptyMap(), seats, viewer, GoFishModule.encodeView(GoFish.view(s, viewer)), 1, true, true)
        val density = Density(shot[4])
        val scene = ImageComposeScene(w, h, density) {
            FamilyGameNightTheme {
                GoFishTable(model, emptyMap(), { colorFor(it) }, {}, {}, {})
            }
        }
        // Let animations settle a little.
        scene.render(0)
        val img = scene.render(1_500_000_000L)
        File(out, "table-${players}p.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        println("wrote table-${players}p.png")
    }
}
