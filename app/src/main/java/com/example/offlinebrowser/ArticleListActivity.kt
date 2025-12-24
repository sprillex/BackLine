package com.example.offlinebrowser

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.ui.ArticleAdapter
import com.example.offlinebrowser.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class ArticleListActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var articleAdapter: ArticleAdapter
    private var feedId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article_list)

        feedId = intent.getIntExtra("FEED_ID", -1)
        val rvArticles = findViewById<RecyclerView>(R.id.rvArticles)

        articleAdapter = ArticleAdapter(
            onArticleClick = { article ->
                // Allow viewing if we have any content (RSS description or full cached HTML)
                if (article.content.isNotEmpty()) {
                    val intent = Intent(this, ArticleViewerActivity::class.java)
                    intent.putExtra("CONTENT", article.content)
                    startActivity(intent)
                }
            },
            onDownloadClick = { article -> viewModel.downloadArticle(article) }
        )

        rvArticles.layoutManager = LinearLayoutManager(this)
        rvArticles.adapter = articleAdapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.getArticlesForFeed(feedId).collect { articles ->
                    articleAdapter.submitList(articles)
                }
            }
        }
    }
}
