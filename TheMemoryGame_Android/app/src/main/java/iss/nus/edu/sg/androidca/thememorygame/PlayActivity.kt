package iss.nus.edu.sg.androidca.thememorygame

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.GridView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.net.URL

class PlayActivity : AppCompatActivity(), AdapterView.OnItemClickListener {

    private lateinit var adapter: MyCustomAdapter
    private var startTime = 0L
    private var running = false
    private lateinit var timerHandler: Handler
    private lateinit var timerRunnable: Runnable
    private var elapsedMillis: Long = 0L
    private var gameWon: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_play)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.play)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize a timer object to set a timer
        timerHandler = Handler(Looper.getMainLooper())

        timerRunnable = Runnable {
            elapsedMillis = SystemClock.elapsedRealtime() - startTime
            val timerText = findViewById<TextView>(R.id.timer)
            timerText.text = TimeUtils.formatElapsedTime(elapsedMillis)

            // Schedule the runnable to run after every 10 centi-seconds
            timerHandler.postDelayed(timerRunnable, 10)
        }

        val gridView = findViewById<GridView>(R.id.playGridView)
        val filenames = intent.getStringArrayExtra("filenames")
        //
        if (filenames != null && filenames.size == 12) {
            adapter = MyCustomAdapter(this, filenames)
            gridView.adapter = adapter
            gridView.onItemClickListener = this
        } else {
            Toast.makeText(this, "Invalid image data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        if (!::adapter.isInitialized) return

        // Run the timer at the first click on item
        if (!running) {
            startTime = SystemClock.elapsedRealtime()
            timerHandler.post(timerRunnable)
            running = true
        }

        // Reveal the image if just one image is flipped (not counting matched images)
        adapter.revealPosition(position)

        // If two images are flipped, then check if the images match
        if (adapter.currentlyFlipped.size == 2) {
            val gridView = findViewById<GridView>(R.id.playGridView)
            val matches = findViewById<TextView>(R.id.matches)
            gridView.isEnabled = false
            // Show images if matched
            if (adapter.checkForMatch()) {
                adapter.finalizeMatch()
                gridView.isEnabled = true
                matches.text = "${adapter.revealedPositions.size / 2}/6 matches"
            }else {
                // Reset images if not matched after 0.8 seconds
                gridView.postDelayed({
                    adapter.resetFlipped()
                    gridView.isEnabled = true
                }, 800)
            }
        }

        // Stop timer and go to leaderboard activity if game is won
        if (adapter.revealedPositions.size == adapter.count && !gameWon) {
            timerHandler.removeCallbacks(timerRunnable)
            // Set game won to true so that clicks are not registered anymore
            gameWon = true
            // Save completion time in database
            val url = "http://10.0.2.2:5187/Home/SaveCompletionTime?completionTime=$elapsedMillis"
            Log.d("Backend Call Link: ", url)
            Thread {
                try {
                    val saveResult: String = URL(url).openStream().bufferedReader().use { it.readText() }
                    runOnUiThread {
                        if (saveResult == "saved") {
                            Toast.makeText(applicationContext, "Completion time saved!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(applicationContext, "Error saving completion time.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(applicationContext, e.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()

            // Go to leaderboard
            timerHandler.postDelayed({
                val intent = Intent(this, LeaderBoardActivity::class.java)
                intent.putExtra("completion_time", elapsedMillis)
                startActivity(intent)
                finish()
            }, 1000)
        }
    }
}