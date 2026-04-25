//
// Copyright(c) 2016-2017 benikabocha.
// Distributed under the MIT License (http://opensource.org/licenses/MIT)
//

#include "ViewerContext.h"

#include <Saba/Base/Path.h>

namespace saba
{
	ViewerContext::ViewerContext()
		: m_uiEnable(true)
		, m_cameraOverride(true)
		, m_clipElapsed(true)
		, m_elapsed(0.0)
		, m_animationTime(0.0)
		, m_msaaEnable(false)
		, m_msaaCount(0)
		, m_frameBufferWidth(0)
		, m_frameBufferHeight(0)
		, m_windowWidth(0)
		, m_windowHeight(0)
		, m_playMode(PlayMode::None)
		, m_shadowEnabled(false)
		, m_mmdGroundShadowColor(0, 0, 0, 1)
	{
		m_workDir.clear();
		m_resourceDir = "Saba/Viewer/resource";
		m_shaderDir = PathUtil::Combine(m_resourceDir, u8"shader");
	}

	bool ViewerContext::Initialize()
	{
		GLTextureObject dummyColorTex;
		if (!dummyColorTex.Create())
		{
			return false;
		}
		glBindTexture(GL_TEXTURE_2D, dummyColorTex);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
		glBindTexture(GL_TEXTURE_2D, 0);
		m_dummyColorTexture = std::move(dummyColorTex);

		GLTextureObject dummyShadowDepthTex;
		if (!dummyShadowDepthTex.Create())
		{
			return false;
		}
		glBindTexture(GL_TEXTURE_2D, dummyShadowDepthTex);
		glTexImage2D(
			GL_TEXTURE_2D,
			0,
			GL_DEPTH_COMPONENT16,
			1,
			1,
			0,
			GL_DEPTH_COMPONENT,
			GL_UNSIGNED_SHORT,
			nullptr
		);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LESS);
		glBindTexture(GL_TEXTURE_2D, 0);
		m_dummyShadowDepthTexture = std::move(dummyShadowDepthTex);

		GLTextureObject captureTex;
		if (!captureTex.Create())
		{
			return false;
		}
		glBindTexture(GL_TEXTURE_2D, captureTex);
		glTexImage2D(
			GL_TEXTURE_2D, 0, GL_RGBA,
			m_frameBufferWidth,
			m_frameBufferHeight,
			0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr
		);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glBindTexture(GL_TEXTURE_2D, 0);
		m_captureTex = std::move(captureTex);

		return true;
	}

	void ViewerContext::Uninitialize()
	{
		m_dummyColorTexture.Release();
		m_dummyShadowDepthTexture.Release();
		m_captureTex.Release();
	}

	void ViewerContext::SetElapsedTime(double elapsed)
	{
		if (m_clipElapsed)
		{
			if (elapsed > 1.0f / 30.0f)
			{
				m_elapsed = 1.0f / 30.0f;
			}
			else
			{
				m_elapsed = elapsed;
			}
		}
		else
		{
			m_elapsed = elapsed;
		}
	}

	bool ViewerContext::ResizeCaptureTexture()
	{
		glBindTexture(GL_TEXTURE_2D, m_captureTex);
		glTexImage2D(
			GL_TEXTURE_2D, 0, GL_RGBA,
			m_frameBufferWidth,
			m_frameBufferHeight,
			0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr
		);
		glBindTexture(GL_TEXTURE_2D, 0);

		return true;
	}

}
