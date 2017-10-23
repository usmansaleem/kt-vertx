// script.js

// create the module and name it blogApp
var blogApp = angular.module('blogApp', ['ngRoute','ngAnimate','ui.bootstrap','ngSanitize']);

//ng-routes
blogApp.config(function($routeProvider) {
        $routeProvider

            // route for the home page
            .when('/', {
                templateUrl : 'pages/home.html',
                controller  : 'mainCtrl'
            })

            // route for the about page
            .when('/about', {
                templateUrl : 'pages/about.html',
                controller  : 'aboutCtrl'
            })

            // route for the notes page
            .when('/notes', {
                templateUrl : 'pages/notes.html',
                controller  : 'notesCtrl'
            })

            .otherwise({
                           redirectTo: '/'
                        });

    });

blogApp.directive('readmoreDirective', function() {
    return function(scope, element, attrs) {
	scope.$watch('blogItem', function(){
		angular.element('article').readmore();
	});	

    }
});

// create the controller and inject Angular's $scope
blogApp.controller('mainCtrl', function($scope, $http, $log, $sce) {
    $scope.pageClass = 'page-home';
    $scope.maxSize = 5;

    $scope.trustBlogHtml = function(html) {
              return $sce.trustAsHtml(html);
            };

    //categories
    $http({
      method: 'GET',
      url: 'rest/blog/listCategories'
    }).then(function successCallback(response) {
        // this callback will be called asynchronously
        // when the response is available
        $scope.categories = response;
      }, function errorCallback(response) {
        // called asynchronously if an error occurs
        // or server returns response with an error status.
        $scope.errorResponse = response;
      });


    //when page changes, fetch new data
    $scope.pageChanged = function() {
        $http.get('rest/blog/blogItems/' + $scope.bigCurrentPage).then(function(response) {
                   $scope.blogItems = response.data;
                   window.scrollTo(0,0);
            });
      };

    //function to get updated blog count, specially when section changes
    $scope.getBlogCount = function() {
       $http.get('rest/blog/blogCount').then(function(response) {
                      $scope.bigTotalItems = response.data;
                      $scope.pageChanged();
           });
    };

    //function to get highestPage
    $scope.getHighestPage = function() {
        $http.get('rest/blog/highestPage').then(function(response) {
            $scope.bigCurrentPage =  response.data;
            $scope.lastPage = $scope.bigCurrentPage;
            $scope.getBlogCount()
        });
    };

    //function to show jumbotron
    $scope.showJumbotron = function() {
        return $scope.lastPage === $scope.bigCurrentPage
    }

    //call initial methods
    $scope.getHighestPage();
    
});

// create the controller and inject Angular's $scope
blogApp.controller('aboutCtrl', function($scope) {
    $scope.pageClass = 'page-about';
});

// create the controller and inject Angular's $scope
blogApp.controller('notesCtrl', function($scope) {
    $scope.pageClass = 'page-notes';
});
