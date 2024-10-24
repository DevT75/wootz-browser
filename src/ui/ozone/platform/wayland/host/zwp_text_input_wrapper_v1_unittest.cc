// Copyright 2020 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "ui/ozone/platform/wayland/host/zwp_text_input_wrapper_v1.h"

#include <string_view>

#include "testing/gmock/include/gmock/gmock.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "ui/ozone/platform/wayland/host/wayland_connection.h"
#include "ui/ozone/platform/wayland/test/mock_zcr_extended_text_input.h"
#include "ui/ozone/platform/wayland/test/mock_zwp_text_input.h"
#include "ui/ozone/platform/wayland/test/test_wayland_server_thread.h"
#include "ui/ozone/platform/wayland/test/test_zcr_text_input_extension.h"
#include "ui/ozone/platform/wayland/test/wayland_test.h"

using ::testing::InSequence;
using ::testing::Mock;

namespace ui {

class TestZWPTextInputWrapperClient : public ZWPTextInputWrapperClient {
 public:
  TestZWPTextInputWrapperClient() = default;
  TestZWPTextInputWrapperClient(const TestZWPTextInputWrapperClient&) = delete;
  TestZWPTextInputWrapperClient& operator=(
      const TestZWPTextInputWrapperClient&) = delete;
  ~TestZWPTextInputWrapperClient() override = default;

  void OnPreeditString(std::string_view text,
                       const std::vector<SpanStyle>& spans,
                       int32_t preedit_cursor) override {}
  void OnCommitString(std::string_view text) override {}
  void OnCursorPosition(int32_t index, int32_t anchor) override {}
  void OnDeleteSurroundingText(int32_t index, uint32_t length) override {}
  void OnKeysym(uint32_t key,
                uint32_t state,
                uint32_t modifiers,
                uint32_t time) override {
    last_keysym_time_ = time;
  }
  void OnSetPreeditRegion(int32_t index,
                          uint32_t length,
                          const std::vector<SpanStyle>& spans) override {}
  void OnClearGrammarFragments(const gfx::Range& range) override {}
  void OnAddGrammarFragment(const ui::GrammarFragment& fragment) override {}
  void OnSetAutocorrectRange(const gfx::Range& range) override {}
  void OnSetVirtualKeyboardOccludedBounds(
      const gfx::Rect& screen_bounds) override {}
  void OnConfirmPreedit(bool keep_selection) override {}
  void OnInputPanelState(uint32_t state) override {}
  void OnModifiersMap(std::vector<std::string> modifiers_map) override {}
  void OnInsertImage(const GURL& src) override {}

  uint32_t last_keysym_time() const { return last_keysym_time_; }

 private:
  uint32_t last_keysym_time_;
};

class ZWPTextInputWrapperV1Test : public WaylandTestSimple {
 public:
  void SetUp() override {
    WaylandTestSimple::SetUp();

    wrapper_ = std::make_unique<ZWPTextInputWrapperV1>(
        connection_.get(), &test_client_, connection_->text_input_manager_v1(),
        connection_->text_input_extension_v1());
  }

 protected:
  TestZWPTextInputWrapperClient test_client_;
  std::unique_ptr<ZWPTextInputWrapperV1> wrapper_;
};

TEST_F(ZWPTextInputWrapperV1Test,
       FinalizeVirtualKeyboardChangesShowInputPanel) {
  PostToServerAndWait([](wl::TestWaylandServerThread* server) {
    InSequence s;
    EXPECT_CALL(*server->text_input_manager_v1()->text_input(),
                ShowInputPanel());
    EXPECT_CALL(*server->text_input_extension_v1()->extended_text_input(),
                FinalizeVirtualKeyboardChanges());
  });

  wrapper_->ShowInputPanel();

  PostToServerAndWait([](wl::TestWaylandServerThread* server) {
    Mock::VerifyAndClearExpectations(
        server->text_input_manager_v1()->text_input());
    Mock::VerifyAndClearExpectations(server->text_input_extension_v1());
  });
}

TEST_F(ZWPTextInputWrapperV1Test,
       FinalizeVirtualKeyboardChangesHideInputPanel) {
  PostToServerAndWait([](wl::TestWaylandServerThread* server) {
    InSequence s;
    EXPECT_CALL(*server->text_input_manager_v1()->text_input(),
                HideInputPanel());
    EXPECT_CALL(*server->text_input_extension_v1()->extended_text_input(),
                FinalizeVirtualKeyboardChanges());
  });

  wrapper_->HideInputPanel();

  PostToServerAndWait([](wl::TestWaylandServerThread* server) {
    Mock::VerifyAndClearExpectations(
        server->text_input_manager_v1()->text_input());
  });

  // The text input extension gets updated and called after another round trip.

  PostToServerAndWait([](wl::TestWaylandServerThread* server) {
    Mock::VerifyAndClearExpectations(
        server->text_input_extension_v1()->extended_text_input());
  });
}

TEST_F(ZWPTextInputWrapperV1Test,
       FinalizeVirtualKeyboardChangesMultipleInputPanelChanges) {
  PostToServerAndWait([](wl::TestWaylandServerThread* server) {
    auto* const mock_text_input = server->text_input_manager_v1()->text_input();
    InSequence s;
    EXPECT_CALL(*mock_text_input, ShowInputPanel());
    EXPECT_CALL(*mock_text_input, HideInputPanel());
    EXPECT_CALL(*mock_text_input, ShowInputPanel());
    EXPECT_CALL(*mock_text_input, HideInputPanel());
    EXPECT_CALL(*mock_text_input, ShowInputPanel());
    EXPECT_CALL(*server->text_input_extension_v1()->extended_text_input(),
                FinalizeVirtualKeyboardChanges());
  });

  wrapper_->ShowInputPanel();
  wrapper_->HideInputPanel();
  wrapper_->ShowInputPanel();
  wrapper_->HideInputPanel();
  wrapper_->ShowInputPanel();

  PostToServerAndWait([](wl::TestWaylandServerThread* server) {
    Mock::VerifyAndClearExpectations(
        server->text_input_manager_v1()->text_input());
  });

  // The text input extension gets updated and called after another round trip.

  PostToServerAndWait([](wl::TestWaylandServerThread* server) {
    Mock::VerifyAndClearExpectations(
        server->text_input_extension_v1()->extended_text_input());
  });
}

TEST_F(ZWPTextInputWrapperV1Test, OnKeySym_TimestampPropagated) {
  uint32_t test_time = 666;

  PostToServerAndWait([test_time](wl::TestWaylandServerThread* server) {
    zwp_text_input_v1_send_keysym(
        server->text_input_manager_v1()->text_input()->resource(), 0, test_time,
        0, 0, 0);
  });

  ASSERT_EQ(test_time, test_client_.last_keysym_time());
}

}  // namespace ui
