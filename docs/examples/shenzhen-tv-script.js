/*
深圳广电全屏
站点：https://www.sztv.com.cn/dianshi.shtml
*/

const hideSelectors = [
    '.right-section',
    '.programs-section',
    'header',
    'footer',
    '.channel-list-section'
];

hideSelectors.forEach(selector => {
    document.querySelectorAll(selector).forEach(el => {
        el.style.display = 'none';
    });
});

document.documentElement.style.cssText = `
    margin:0;
    padding:0;
    width:100vw;
    height:100vh;
    overflow:hidden;
    background:black;
`;

document.body.style.cssText = `
    margin:0;
    padding:0;
    width:100vw;
    height:100vh;
    overflow:hidden;
    background:black;
`;

const container = document.querySelector('.player-section');

if (container) {
    container.style.cssText = `
        position:fixed;
        left:0;
        top:0;
        width:100vw;
        height:100vh;
        margin:0;
        padding:0;
        z-index:9999;
        background:black;
    `;
}

const iframe = document.querySelector('#livePlayerIframe');

if (iframe) {
    iframe.style.cssText = `
        position:fixed;
        left:0;
        top:0;
        width:100vw;
        height:100vh;
        border:0;
        background:black;
        z-index:10000;
    `;

    let count = 0;

    const timer = setInterval(() => {
        try {
            const doc = iframe.contentDocument || iframe.contentWindow.document;
            const video = doc.querySelector('video');

            if (video) {
                video.muted = false;
                video.volume = 1;

                video.style.cssText = `
                    position:fixed;
                    left:0;
                    top:0;
                    width:100vw;
                    height:100vh;
                    object-fit:cover;
                    background:black;
                    z-index:2147483647;
                `;

                video.play();
                clearInterval(timer);
            }
        } catch (e) {}

        count++;

        if (count > 20) {
            clearInterval(timer);
        }
    }, 500);
}