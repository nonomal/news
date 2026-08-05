package org.vestifeed.entries

fun interface EntriesAdapterCallback {
    fun onItemClick(item: EntriesAdapter.Item)
    fun onImageLongClick(item: EntriesAdapter.Item) {}
}