// Copyright 2024 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ASH_PICKER_SEARCH_MOCK_SEARCH_PICKER_CLIENT_H_
#define ASH_PICKER_SEARCH_MOCK_SEARCH_PICKER_CLIENT_H_

#include <string>

#include "ash/ash_export.h"
#include "ash/public/cpp/picker/mock_picker_client.h"

namespace ash {

// Mock client used for search tests.
// By default:
// - `StartCrosSearch` and `FetchGifSearch` will store the supplied
//   callback which can be obtained using `cros_search_callback()` and
//   `gif_search_callback()` respectively.
// - `GetSharedURLLoaderFactory` and `ShowEditor` will cause the current test to
//   fail.
// These behaviours can be overridden with `WillOnce` and `WillRepeatedly` if
// necessary.
class ASH_EXPORT MockSearchPickerClient : public MockPickerClient {
 public:
  MockSearchPickerClient();
  ~MockSearchPickerClient() override;

  // Set by the default `StartCrosSearch` behaviour. If the behaviour is
  // overridden, this may not be set on a `StartCrosSearch` callback.
  CrosSearchResultsCallback& cros_search_callback();

  // Set by the default `FetchGifSearch` behaviour. If the behaviour is
  // overridden, this may not be set on a `FetchGifSearch` callback.
  FetchGifsCallback& gif_search_callback();

  // Use `Invoke(&client, &MockSearchPickerClient::FetchGifSearchToSetCallback)`
  // as a `FetchGifSearch` action to set `gif_search_callback_` when
  // `FetchGifSearch` is called.
  // This is already done by default in the constructor, but provided here for
  // convenience if a test requires this action when overriding the default
  // action.
  // TODO: b/73967242 - Use a gMock action for this once gMock supports
  // move-only arguments in `SaveArg`.
  void FetchGifSearchToSetCallback(const std::string& query,
                                   FetchGifsCallback callback);

 private:
  CrosSearchResultsCallback cros_search_callback_;
  FetchGifsCallback gif_search_callback_;
};

}  // namespace ash

#endif  // ASH_PICKER_SEARCH_MOCK_SEARCH_PICKER_CLIENT_H_
