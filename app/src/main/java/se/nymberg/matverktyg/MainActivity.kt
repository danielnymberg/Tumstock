package se.nymberg.matverktyg

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import se.nymberg.matverktyg.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var ruler: RulerController
    private lateinit var photo: PhotoController
    private lateinit var level: LevelController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Android 15 (targetSdk 35) ritar kant-till-kant: padda för systemfälten
        // så att knappar aldrig hamnar under navigeringsfältet.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        ruler = RulerController(this, binding).also { it.init() }
        photo = PhotoController(this, binding).also { it.init() }
        level = LevelController(this, binding).also { it.init() }

        binding.bottomNav.setOnItemSelectedListener { item ->
            binding.screenRuler.visibility = View.GONE
            binding.screenPhoto.visibility = View.GONE
            binding.screenLevel.visibility = View.GONE
            when (item.itemId) {
                R.id.navRuler -> {
                    binding.screenRuler.visibility = View.VISIBLE
                    binding.topTitle.setText(R.string.tab_ruler)
                }
                R.id.navPhoto -> {
                    binding.screenPhoto.visibility = View.VISIBLE
                    binding.topTitle.setText(R.string.tab_photo)
                }
                R.id.navLevel -> {
                    binding.screenLevel.visibility = View.VISIBLE
                    binding.topTitle.setText(R.string.tab_level)
                }
            }
            true
        }
        binding.bottomNav.selectedItemId = R.id.navRuler
    }

    override fun onResume() {
        super.onResume()
        level.resume()
    }

    override fun onPause() {
        super.onPause()
        level.pause()
    }
}
