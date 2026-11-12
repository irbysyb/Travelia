package com.travelia

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.travelia.databinding.ItemSliderArticleBinding

class ArticleAdapter(
    private val articles: List<ArticleModel>,
    private val onItemClick: (ArticleModel) -> Unit
) : RecyclerView.Adapter<ArticleAdapter.ArticleViewHolder>() {

    inner class ArticleViewHolder(val binding: ItemSliderArticleBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleViewHolder {
        val binding = ItemSliderArticleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ArticleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ArticleViewHolder, position: Int) {
        val article = articles[position]

        holder.binding.tvArticleTitle.text = article.title
        holder.binding.tvArticleDesc.text = article.shortDescription

        // Panggil URL gambar dari Firestore
        if (!article.imageUrl.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(article.imageUrl.trim())
                .error(R.drawable.img_article_hiace) // Tampilkan gambar lokal HANYA jika URL error/rusak
                .into(holder.binding.imgArticleThumb)
        } else {
            holder.binding.imgArticleThumb.setImageResource(R.drawable.img_article_hiace)
        }

        holder.itemView.setOnClickListener {
            onItemClick(article)
        }
    }

    override fun getItemCount(): Int = articles.size
}