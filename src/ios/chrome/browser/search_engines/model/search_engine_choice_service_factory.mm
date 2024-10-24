// Copyright 2024 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/search_engines/model/search_engine_choice_service_factory.h"

#import <memory>

#import "base/check_deref.h"
#import "components/keyed_service/ios/browser_state_dependency_manager.h"
#import "components/search_engines/search_engine_choice/search_engine_choice_service.h"
#import "components/variations/service/variations_service.h"
#import "ios/chrome/browser/shared/model/application_context/application_context.h"
#import "ios/chrome/browser/shared/model/browser_state/browser_state_otr_helper.h"
#import "ios/chrome/browser/shared/model/browser_state/chrome_browser_state.h"
#import "ios/web/public/browser_state.h"

namespace ios {

SearchEngineChoiceServiceFactory::SearchEngineChoiceServiceFactory()
    : BrowserStateKeyedServiceFactory(
          "SearchEngineChoiceServiceFactory",
          BrowserStateDependencyManager::GetInstance()) {}

SearchEngineChoiceServiceFactory::~SearchEngineChoiceServiceFactory() = default;

// static
SearchEngineChoiceServiceFactory*
SearchEngineChoiceServiceFactory::GetInstance() {
  static base::NoDestructor<SearchEngineChoiceServiceFactory> factory;
  return factory.get();
}

// static
search_engines::SearchEngineChoiceService*
SearchEngineChoiceServiceFactory::GetForBrowserState(
    ChromeBrowserState* browser_state) {
  return static_cast<search_engines::SearchEngineChoiceService*>(
      GetInstance()->GetServiceForBrowserState(browser_state, true));
}

std::unique_ptr<KeyedService>
SearchEngineChoiceServiceFactory::BuildServiceInstanceFor(
    web::BrowserState* context) const {
  int variations_country_id = country_codes::kCountryIDUnknown;
  if (variations::VariationsService* variations_service =
          GetApplicationContext()->GetVariationsService()) {
    // Need to use `GetLatestCountry()` for consistency with:
    // chrome/browser/search_engine_choice/
    // search_engine_choice_service_factory.cc
    variations_country_id = country_codes::CountryStringToCountryID(
        base::ToUpperASCII(variations_service->GetLatestCountry()));
  }

  ChromeBrowserState* browser_state =
      ChromeBrowserState::FromBrowserState(context);
  return std::make_unique<search_engines::SearchEngineChoiceService>(
      CHECK_DEREF(browser_state->GetPrefs()), variations_country_id);
}

web::BrowserState* SearchEngineChoiceServiceFactory::GetBrowserStateToUse(
    web::BrowserState* context) const {
  return GetBrowserStateRedirectedInIncognito(context);
}

}  // namespace ios
