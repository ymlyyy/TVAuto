(function() {
    var layerId = 'tvauto_loading_layer';
    if (!document.getElementById(layerId)) {
        var css = `
            @keyframes pulseJump {
                0%, 100% { transform: translateY(0) scale(1); }
                30% { transform: translateY(-0.12em) scale(1.03); }
                60% { transform: translateY(0.02em) scale(0.98); }
            }
            #${layerId} {
                position: fixed; top: 0; left: 0; right: 0; bottom: 0;
                width: 100vw; height: 100vh;
                background: rgba(0, 0, 0, 0.65); 
                z-index: 2147483647; 
                display: flex; justify-content: center; align-items: center;
                pointer-events: none;
                transition: opacity 0.3s;
                transform: none !important;
                margin: 0 !important;
                padding: 0 !important;
                box-sizing: border-box;
            }
            .tv-text-container {
                font-family: sans-serif; font-weight: 900; font-size: 5vw;
                display: flex; white-space: nowrap; letter-spacing: 0.05em;
                transform: none;
            }
            .tv-char {
                display: inline-block;
                animation: pulseJump 0.8s infinite ease-out;
            }
        `;
        var style = document.createElement('style');
        style.appendChild(document.createTextNode(css));
        document.head.appendChild(style);

        var layer = document.createElement('div');
        layer.id = layerId;
        layer.innerHTML = `
            <div class="tv-text-container">
                <span class="tv-char" style="color:#33383C; animation-delay:0s">T</span>
                <span class="tv-char" style="color:#33383C; animation-delay:0.04s">V</span>
                <span class="tv-char" style="color:#0079FB; animation-delay:0.08s">A</span>
                <span class="tv-char" style="color:#0079FB; animation-delay:0.12s">u</span>
                <span class="tv-char" style="color:#0079FB; animation-delay:0.16s">t</span>
                <span class="tv-char" style="color:#0079FB; animation-delay:0.20s">o</span>
            </div>`;
        document.documentElement.appendChild(layer);
    }

    function showLoading() {
        var el = document.getElementById(layerId);
        if (el) { el.style.opacity = '1'; el.style.display = 'flex'; }
    }
    function hideLoading() {
        var el = document.getElementById(layerId);
        if (el) { 
            el.style.opacity = '0'; 
            setTimeout(() => { if(el.style.opacity === '0') el.style.display = 'none'; }, 300);
        }
    }

    showLoading();

    window.__VIDEO_RESIZE_INJECTED__ = true;
    var url = window.location.href.toLowerCase();
    var containsDouyu = url.includes('douyu');
    var containsM3u8 = url.includes('.m3u8');
    var containsM3u = url.includes('.m3u');
    var containsHuya = url.includes('huya');
    var needScaleHalf = !(containsDouyu || containsM3u8 || containsM3u || containsHuya);
    let count = 0;

    var interval = setInterval(function() {
        console.log("onPageStarted-> get_video");
        var video = document.querySelector('video');
        
        if (video) {
            if (!video.getAttribute('data-tvauto-bound')) {
                video.addEventListener('waiting', showLoading);
                video.addEventListener('loadstart', showLoading);
                video.addEventListener('seeking', showLoading);
                video.addEventListener('playing', hideLoading);
                video.addEventListener('canplay', hideLoading);
                video.addEventListener('seeked', hideLoading);
                video.setAttribute('data-tvauto-bound', 'true');
            }
            document.body.style.transformOrigin = 'top left';

            if (needScaleHalf) {
                document.body.style.transform = 'scale(0.5)';
                video.style.width = 'calc(200vh * 16 / 9)';
                video.style.height = '200vh';
            } else {
                document.body.style.transform = '';
                video.style.width = 'calc(100vh * 16 / 9)';
                video.style.height = '100vh';
            }

            video.style.position = 'fixed';
            video.style.top = '0';
            video.style.left = '0';
            video.style.objectFit = 'cover';
            video.style.zIndex = '9999';
            video.style.backgroundColor = 'black';
            video.muted = false;
            video.volume = 1.0;
            video.play();
            
            let el = video;
            while (el) {
                el.style.overflow = 'visible';
                if (el.style && el !== document.body) el.style.zIndex = '9999';
                el = el.parentElement;
            }
            if (!video.paused && video.readyState >= 3 && video.mozHasAudio !== false) {
                console.log("onPageStarted-> 处理完成");
                hideLoading();
                clearInterval(interval);
            }
            count++;
            if(count > 10){
                console.log("onPageStarted-> 超时自动关闭定时器");
                clearInterval(interval);
            }
        } else {
            showLoading();
        }
    }, 500);
})();