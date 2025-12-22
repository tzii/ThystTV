 package com.github.andreyasadchy.xtra.ui.common
 
 /**
  * Sealed class representing the state of UI data loading.
  * Provides a consistent way to handle loading, success, and error states across the app.
  */
 sealed class UiState<out T> {
     
     /**
      * Represents the initial loading state before data is available.
      */
     data object Loading : UiState<Nothing>()
     
     /**
      * Represents a successful data load with the resulting data.
      * @param data The loaded data
      */
     data class Success<T>(val data: T) : UiState<T>()
     
     /**
      * Represents an error state with an error message and optional retry action.
      * @param message The error message to display
      * @param exception The optional exception that caused the error
      * @param retry Optional callback to retry the failed operation
      */
     data class Error(
         val message: String,
         val exception: Throwable? = null,
         val retry: (() -> Unit)? = null
     ) : UiState<Nothing>()
     
     /**
      * Represents an empty state when data loaded successfully but the result is empty.
      */
     data object Empty : UiState<Nothing>()
     
     /**
      * Returns true if this state is [Loading].
      */
     val isLoading: Boolean get() = this is Loading
     
     /**
      * Returns true if this state is [Success].
      */
     val isSuccess: Boolean get() = this is Success
     
     /**
      * Returns true if this state is [Error].
      */
     val isError: Boolean get() = this is Error
     
     /**
      * Returns the data if this is a [Success] state, otherwise null.
      */
     fun getOrNull(): T? = (this as? Success)?.data
     
     /**
      * Returns the data if this is a [Success] state, otherwise returns the default value.
      */
     fun getOrDefault(default: @UnsafeVariance T): T = (this as? Success)?.data ?: default
     
     /**
      * Maps the data in a [Success] state using the provided transform function.
      * Returns the same state for [Loading], [Error], and [Empty] states.
      */
     inline fun <R> map(transform: (T) -> R): UiState<R> = when (this) {
         is Loading -> Loading
         is Success -> Success(transform(data))
         is Error -> Error(message, exception, retry)
         is Empty -> Empty
     }
     
     /**
      * Executes the given block if this is a [Success] state.
      */
     inline fun onSuccess(action: (T) -> Unit): UiState<T> {
         if (this is Success) action(data)
         return this
     }
     
     /**
      * Executes the given block if this is an [Error] state.
      */
     inline fun onError(action: (Error) -> Unit): UiState<T> {
         if (this is Error) action(this)
         return this
     }
     
     /**
      * Executes the given block if this is a [Loading] state.
      */
     inline fun onLoading(action: () -> Unit): UiState<T> {
         if (this is Loading) action()
         return this
     }
 }
