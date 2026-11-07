package `in`.drarunbabu.drsharmila.jananisync.di

import `in`.drarunbabu.drsharmila.jananisync.data.LaborDataRepository
import `in`.drarunbabu.drsharmila.jananisync.data.auth.AuthRepository
import `in`.drarunbabu.drsharmila.jananisync.data.auth.createAuthRepository
import `in`.drarunbabu.drsharmila.jananisync.data.createLaborDataRepository
import `in`.drarunbabu.drsharmila.jananisync.data.preferences.UserPreferencesRepository
import `in`.drarunbabu.drsharmila.jananisync.data.preferences.createAppDataStore
import `in`.drarunbabu.drsharmila.jananisync.data.privacy.PrivacyConsentRepository
import `in`.drarunbabu.drsharmila.jananisync.data.privacy.createPrivacyConsentRepository
import `in`.drarunbabu.drsharmila.jananisync.data.rbac.OrganizationAccessRepository
import `in`.drarunbabu.drsharmila.jananisync.data.rbac.createOrganizationAccessRepository
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.*
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { createAppDataStore() }
    single { UserPreferencesRepository(get()) }
    single<AuthRepository> { createAuthRepository() }
    single<PrivacyConsentRepository> { createPrivacyConsentRepository() }
    single<OrganizationAccessRepository> { createOrganizationAccessRepository() }
    single<LaborDataRepository> { createLaborDataRepository() }
    viewModel { AppViewModel(get()) }
    viewModel { LoginViewModel(get()) }
    viewModel { ResetPasswordViewModel(get()) }
    viewModel { SignUpViewModel(get()) }
    viewModel { PrivacyConsentViewModel(get(), get()) }
    viewModel { EmailVerificationViewModel(get()) }
    viewModel { OrganizationAccessViewModel(get()) }
    viewModel { LabourCareViewModel(get()) }
}
