package language

import dagger.Component

@Component
interface LanguageFactoryComponent {
    fun getInstance(): LanguageFactory
}